package com.smartreview.smartreview.model;

import com.smartreview.smartreview.model.enums.IssueCategory;
import com.smartreview.smartreview.model.enums.Severity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Entity
@Table(name = "review_issues")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_review_id", nullable = false)
    private FileReview fileReview;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    private Integer lineNumber;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(length = 2000)
    private String suggestedFix;

}