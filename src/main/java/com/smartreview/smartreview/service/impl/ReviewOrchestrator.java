package com.smartreview.smartreview.service.impl;

import com.smartreview.smartreview.model.FileReview;
import com.smartreview.smartreview.model.ReviewIssue;
import com.smartreview.smartreview.model.ReviewJob;
import com.smartreview.smartreview.model.enums.ReviewStatus;
import com.smartreview.smartreview.repository.ReviewJobRepository;
import com.smartreview.smartreview.service.CodeReviewProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
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
    public ReviewJob startReview(String repoUrl) {
        ReviewJob job = ReviewJob.builder()
                .repoUrl(repoUrl)
                .status(ReviewStatus.IN_PROGRESS)
                .build();
        job = reviewJobRepository.save(job);
        log.info("Started review job {} for {}", job.getId(), repoUrl);

        Path clonedPath = null;
        try {
            Map<String, String> sourceFiles = repoService.cloneAndExtract(repoUrl);

            if (sourceFiles.isEmpty()) {
                return failJob(job, "No supported source files found in repository.");
            }

            List<FileReview> fileReviews = new ArrayList<>();
            for (Map.Entry<String, String> entry : sourceFiles.entrySet()) {
                String filePath = entry.getKey();
                String content  = entry.getValue();
                String language = repoService.detectLanguage(filePath);

                log.debug("Sending {} to AI review...", filePath);
                FileReview fileReview = codeReviewProvider.review(filePath, language, content);
                fileReview.setReviewJob(job);

                fileReview.getIssues().forEach(issue -> issue.setFileReview(fileReview));
                fileReviews.add(fileReview);
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            job.setFileReviews(fileReviews);
            aggregateStats(job, fileReviews);
            job.setStatus(ReviewStatus.COMPLETED);
            job.setCompletedAt(java.time.LocalDateTime.now());

            log.info("Review job {} completed. Score: {}, Files: {}, Bugs: {}",
                    job.getId(), job.getOverallScore(), job.getFilesReviewed(), job.getTotalBugs());

        } catch (Exception e) {
            log.error("Review job {} failed: {}", job.getId(), e.getMessage(), e);
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
