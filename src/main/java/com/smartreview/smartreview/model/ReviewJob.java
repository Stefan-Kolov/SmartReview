package com.smartreview.smartreview.model;

import com.smartreview.smartreview.model.enums.ReviewStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "review_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String repoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    private Integer overallScore;

    private Integer totalBugs;
    private Integer totalSecurityIssues;
    private Integer totalStyleIssues;
    private Integer filesReviewed;

    @Column(length = 1000)
    private String errorMessage;

    @OneToMany(mappedBy = "reviewJob", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<FileReview> fileReviews = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (status == null) status = ReviewStatus.PENDING;
    }
}