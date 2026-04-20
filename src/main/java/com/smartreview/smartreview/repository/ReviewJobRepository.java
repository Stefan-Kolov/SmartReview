package com.smartreview.smartreview.repository;

import com.smartreview.smartreview.model.ReviewJob;
import com.smartreview.smartreview.model.enums.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewJobRepository extends JpaRepository<ReviewJob, String> {
    List<ReviewJob> findAllByOrderByCreatedAtDesc();
    List<ReviewJob> findByStatus(ReviewStatus status);
}
