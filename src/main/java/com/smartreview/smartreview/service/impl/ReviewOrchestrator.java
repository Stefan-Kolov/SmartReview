package com.smartreview.smartreview.service.impl;

import com.smartreview.smartreview.model.FileReview;
import com.smartreview.smartreview.model.ReviewIssue;
import com.smartreview.smartreview.model.ReviewJob;
import com.smartreview.smartreview.model.User;
import com.smartreview.smartreview.model.enums.ReviewStatus;
import com.smartreview.smartreview.repository.ReviewJobRepository;
import com.smartreview.smartreview.service.CodeReviewProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewOrchestrator {

    private final RepoService repoService;
    private final CodeReviewProvider codeReviewProvider;
    private final ReviewJobRepository reviewJobRepository;

    @Transactional
    public ReviewJob startReview(String repoUrl, User user) {
        ReviewJob job = ReviewJob.builder()
                .repoUrl(repoUrl)
                .user(user)
                .status(ReviewStatus.IN_PROGRESS)
                .build();
        job = reviewJobRepository.save(job);

        try {
            Map<String, String> sourceFiles = repoService.cloneAndExtract(repoUrl);
            if (sourceFiles.isEmpty()) return failJob(job, "No supported source files found in repository.");

            List<Map.Entry<String, String>> entries = new ArrayList<>(sourceFiles.entrySet());
            List<FileReview> allResults = new ArrayList<>();

            int BATCH_SIZE = 5;
            for (int i = 0; i < entries.size(); i += BATCH_SIZE) {
                List<Map.Entry<String, String>> batch =
                        entries.subList(i, Math.min(i + BATCH_SIZE, entries.size()));

                log.info("Processing batch {}/{}", (i/BATCH_SIZE)+1, (int)Math.ceil((double)entries.size()/BATCH_SIZE));

                List<FileReview> batchResults = codeReviewProvider.reviewBatch(batch, repoService);

                for (FileReview fr : batchResults) {
                    fr.setReviewJob(job);
                    fr.getIssues().forEach(issue -> issue.setFileReview(fr));
                    allResults.add(fr);
                }

                if (i + BATCH_SIZE < entries.size()) {
                    log.debug("Batch complete. Cooling down for 1.5s to match TPM...");
                    Thread.sleep(1500);
                }
            }

            job.setFileReviews(allResults);
            aggregateStats(job, allResults);
            job.setStatus(ReviewStatus.COMPLETED);
            job.setCompletedAt(java.time.LocalDateTime.now());

        } catch (Exception e) {
            log.error("Review job failed: {}", e.getMessage());
            return failJob(job, e.getMessage());
        } finally {
            repoService.cleanup(repoUrl);
        }
        return reviewJobRepository.save(job);
    }

    private void aggregateStats(ReviewJob job, List<FileReview> fileReviews) {
        int totalBugs      = 0;
        int totalSecurity  = 0;
        int totalStyle     = 0;
        int scoreSum       = 0;

        for (FileReview fr : fileReviews) {
            scoreSum += fr.getFileScore() != null ? fr.getFileScore() : 0;
            for (ReviewIssue issue : fr.getIssues()) {
                switch (issue.getCategory()) {
                    case BUG        -> totalBugs++;
                    case SECURITY   -> totalSecurity++;
                    case STYLE      -> totalStyle++;
                    default         -> {}
                }
            }
        }

        int fileCount = fileReviews.size();
        job.setFilesReviewed(fileCount);
        job.setTotalBugs(totalBugs);
        job.setTotalSecurityIssues(totalSecurity);
        job.setTotalStyleIssues(totalStyle);
        job.setOverallScore(fileCount > 0 ? scoreSum / fileCount : 0);
    }

    private ReviewJob failJob(ReviewJob job, String reason) {
        job.setStatus(ReviewStatus.FAILED);
        job.setErrorMessage(reason);
        job.setCompletedAt(java.time.LocalDateTime.now());
        return reviewJobRepository.save(job);
    }
}
