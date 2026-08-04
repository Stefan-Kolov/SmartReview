package com.smartreview.smartreview.service.impl;

import com.smartreview.smartreview.model.FileReview;
import com.smartreview.smartreview.model.ReviewIssue;
import com.smartreview.smartreview.model.ReviewJob;
import com.smartreview.smartreview.model.User;
import com.smartreview.smartreview.model.enums.ReviewStatus;
import com.smartreview.smartreview.repository.ReviewJobRepository;
import com.smartreview.smartreview.service.CodeReviewProvider;
import com.smartreview.smartreview.service.impl.providers.ReviewProviderFactory;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
    private final ReviewProgressService progressService;

    private static final int MAX_CONCURRENT = 3;
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT);

    @Transactional
    public ReviewJob createJob(String repoUrl, String providerName, User user) {
        ReviewJob job = ReviewJob.builder()
                .repoUrl(repoUrl)
                .user(user)
                .status(ReviewStatus.PENDING)
                .provider(providerName)
                .build();
        return reviewJobRepository.save(job);
    }

    @Async("reviewTaskExecutor")
    public void startReviewAsync(String jobId, String repoUrl, String providerName, String apiKey) {
        CodeReviewProvider reviewProvider = providerFactory.create(providerName, apiKey);

        log.info("Starting review [jobId={}] using provider: {}", jobId, providerName);

        ReviewJob job = reviewJobRepository.findById(jobId).orElseThrow();
        job.setStatus(ReviewStatus.IN_PROGRESS);
        reviewJobRepository.save(job);

        try {
            progressService.sendProgress(jobId, 0, 0, "Cloning repository...", "CLONING");

            Map<String, String> sourceFiles = repoService.cloneAndExtract(repoUrl);

            if (sourceFiles.isEmpty()) {
                failJob(job, "No supported source files found.");
                progressService.sendError(jobId, "No supported source files found.");
                return;
            }

            List<Map.Entry<String, String>> entries = new ArrayList<>(sourceFiles.entrySet());
            int total = entries.size();

            progressService.sendProgress(jobId, 0, total, "Starting review...", "REVIEWING");

            List<CompletableFuture<FileReview>> futures = new ArrayList<>();
            int[] processed = {0};

            for (Map.Entry<String, String> entry : entries) {
                CompletableFuture<FileReview> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        semaphore.acquire();
                        try {
                            progressService.sendProgress(
                                    jobId,
                                    processed[0],
                                    total,
                                    entry.getKey().split("[/\\\\]")[entry.getKey().split("[/\\\\]").length - 1],
                                    "REVIEWING"
                            );
                            FileReview result = reviewProvider.review(
                                    entry.getKey(),
                                    repoService.detectLanguage(entry.getKey()),
                                    entry.getValue()
                            );
                            processed[0]++;
                            progressService.sendProgress(
                                    jobId,
                                    processed[0],
                                    total,
                                    entry.getKey().split("[/\\\\]")[entry.getKey().split("[/\\\\]").length - 1],
                                    "REVIEWING"
                            );
                            return result;
                        } finally {
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });
                futures.add(future);
            }

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
            reviewJobRepository.save(job);

            progressService.sendComplete(jobId);

        } catch (Exception e) {
            log.error("Review job failed: {}", e.getMessage());
            failJob(job, e.getMessage());
            progressService.sendError(jobId, "Review failed. Please try again.");
        }
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

    private void failJob(ReviewJob job, String reason) {
        String msg = reason != null && reason.length() > 100
                ? reason.substring(0, 100) + "..." : reason;
        job.setStatus(ReviewStatus.FAILED);
        job.setErrorMessage(msg);
        job.setCompletedAt(java.time.LocalDateTime.now());
        reviewJobRepository.save(job);
    }
}