package com.smartreview.smartreview.service.impl;

import com.smartreview.smartreview.model.FileReview;
import com.smartreview.smartreview.model.ReviewIssue;
import com.smartreview.smartreview.model.ReviewJob;
import com.smartreview.smartreview.model.User;
import com.smartreview.smartreview.model.enums.ReviewStatus;
import com.smartreview.smartreview.repository.ReviewJobRepository;
import com.smartreview.smartreview.service.CodeReviewProvider;
import com.smartreview.smartreview.service.impl.providers.ReviewProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewOrchestrator {

    private final RepoService repoService;
    private final ReviewJobRepository reviewJobRepository;
    private final ReviewProviderFactory providerFactory;
    private static final int MAX_CONCURRENT = 3;
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT);

    @Transactional
    public ReviewJob startReview(String repoUrl, String providerName, String apiKey, User user) {
        CodeReviewProvider reviewProvider = providerFactory.create(providerName, apiKey);
        ReviewJob job = ReviewJob.builder()
                .repoUrl(repoUrl)
                .user(user)
                .status(ReviewStatus.IN_PROGRESS)
                .provider(providerName)
                .build();
        job = reviewJobRepository.save(job);

        try {
            Map<String, String> sourceFiles = repoService.cloneAndExtract(repoUrl);
            if (sourceFiles.isEmpty()) return failJob(job, "No supported source files found in repository.");

            List<Map.Entry<String, String>> entries = new ArrayList<>(sourceFiles.entrySet());
            log.info("Starting parallel review of {} files (max {} concurrent)", entries.size(), MAX_CONCURRENT);

            List<CompletableFuture<FileReview>> futures = new ArrayList<>(entries.stream()
                    .map(entry -> CompletableFuture.supplyAsync(() -> {
                        try {
                            semaphore.acquire();
                            try {
                                return reviewProvider.review(
                                        entry.getKey(),
                                        repoService.detectLanguage(entry.getKey()),
                                        entry.getValue()
                                );
                            } finally {
                                semaphore.release();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    }))
                    .toList());

            List<FileReview> allResults = new ArrayList<>(futures.stream()
                    .map(CompletableFuture::join)
                    .toList());

            for (FileReview fr : allResults) {
                fr.setReviewJob(job);
                fr.setContent(sourceFiles.get(fr.getFilePath()));
                fr.getIssues().forEach(issue -> issue.setFileReview(fr));
            }

            job.setFileReviews(allResults);
            aggregateStats(job, allResults);
            job.setStatus(ReviewStatus.COMPLETED);
            job.setCompletedAt(java.time.LocalDateTime.now());

        } catch (Exception e) {
            log.error("Review job failed: {}", e.getMessage());
            return failJob(job, e.getMessage());
        }

        return reviewJobRepository.save(job);
    }

    private void aggregateStats(ReviewJob job, List<FileReview> fileReviews) {
        int totalBugs = 0, totalSecurity = 0, totalStyle = 0, scoreSum = 0;

        for (FileReview fr : fileReviews) {
            scoreSum += fr.getFileScore() != null ? fr.getFileScore() : 0;
            for (ReviewIssue issue : fr.getIssues()) {
                switch (issue.getCategory()) {
                    case BUG      -> totalBugs++;
                    case SECURITY -> totalSecurity++;
                    case STYLE    -> totalStyle++;
                    default       -> {}
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