package com.smartreview.smartreview.model.dto;

import com.smartreview.smartreview.model.FileReview;
import com.smartreview.smartreview.model.ReviewIssue;
import com.smartreview.smartreview.model.ReviewJob;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class ReviewJobResponse {

    private String id;
    private String repoUrl;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private Integer overallScore;
    private Integer totalBugs;
    private Integer totalSecurityIssues;
    private Integer totalStyleIssues;
    private Integer filesReviewed;
    private String errorMessage;
    private String provider;
    private Long durationSeconds;

    private List<FileReviewDto> fileReviews;

    public static ReviewJobResponse from(ReviewJob job) {
        return ReviewJobResponse.builder()
                .id(job.getId())
                .repoUrl(job.getRepoUrl())
                .status(job.getStatus().name())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .overallScore(job.getOverallScore())
                .totalBugs(job.getTotalBugs())
                .totalSecurityIssues(job.getTotalSecurityIssues())
                .totalStyleIssues(job.getTotalStyleIssues())
                .filesReviewed(job.getFilesReviewed())
                .provider(job.getProvider())
                .durationSeconds(job.getCompletedAt() != null && job.getCreatedAt() != null
                        ? java.time.Duration.between(job.getCreatedAt(), job.getCompletedAt()).getSeconds() : null)
                .errorMessage(job.getErrorMessage())
                .build();
    }

    public static ReviewJobResponse fromDetailed(ReviewJob job) {
        ReviewJobResponse response = from(job);
        if (job.getFileReviews() != null) {
            response.setFileReviews(
                    job.getFileReviews().stream()
                            .map(FileReviewDto::from)
                            .collect(Collectors.toList())
            );
        }
        return response;
    }

    @Data
    @Builder
    public static class FileReviewDto {
        private String id;
        private String filePath;
        private String language;
        private Integer fileScore;
        private String summary;
        private String content;
        private List<IssueDto> issues;

        public static FileReviewDto from(FileReview fr) {
            return FileReviewDto.builder()
                    .id(fr.getId())
                    .filePath(fr.getFilePath())
                    .language(fr.getLanguage())
                    .fileScore(fr.getFileScore())
                    .summary(fr.getSummary())
                    .content(fr.getContent())
                    .issues(fr.getIssues() == null ? List.of() :
                            fr.getIssues().stream().map(IssueDto::from).collect(Collectors.toList()))
                    .build();
        }
    }

    @Data
    @Builder
    public static class IssueDto {
        private String id;
        private String category;
        private String severity;
        private Integer lineNumber;
        private String description;
        private String suggestedFix;

        public static IssueDto from(ReviewIssue issue) {
            return IssueDto.builder()
                    .id(issue.getId())
                    .category(issue.getCategory().name())
                    .severity(issue.getSeverity().name())
                    .lineNumber(issue.getLineNumber())
                    .description(issue.getDescription())
                    .suggestedFix(issue.getSuggestedFix())
                    .build();
        }
    }
}