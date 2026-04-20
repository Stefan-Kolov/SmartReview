package com.smartreview.smartreview.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "file_reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_job_id", nullable = false)
    private ReviewJob reviewJob;

    @Column(nullable = false)
    private String filePath;

    private String language;

    private Integer fileScore;

    @Column(length = 2000)
    private String summary;

    @OneToMany(mappedBy = "fileReview", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ReviewIssue> issues = new ArrayList<>();
}
