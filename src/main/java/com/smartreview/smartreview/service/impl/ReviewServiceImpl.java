package com.smartreview.smartreview.service.impl;

import com.smartreview.smartreview.model.ReviewJob;
import com.smartreview.smartreview.model.User;
import com.smartreview.smartreview.model.dto.ReviewJobResponse;
import com.smartreview.smartreview.model.dto.ReviewRequest;
import com.smartreview.smartreview.model.exceptions.ResourceNotFoundException;
import com.smartreview.smartreview.repository.ReviewJobRepository;
import com.smartreview.smartreview.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewOrchestrator reviewOrchestrator;
    private final ReviewJobRepository reviewJobRepository;

    @Override
    public ReviewJobResponse submitReview(ReviewRequest request, User user) {
        ReviewJob job = reviewOrchestrator.createJob(
                request.getRepoUrl(),
                request.getProvider(),
                user
        );

        reviewOrchestrator.startReviewAsync(
                job.getId(),
                request.getRepoUrl(),
                request.getProvider(),
                request.getApiKey()
        );
        return ReviewJobResponse.from(job);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewJobResponse> listMyReviews(User user) {
        return reviewJobRepository.findAllByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(ReviewJobResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewJobResponse getReviewDetails(String id) {
        return reviewJobRepository.findById(id)
                .map(ReviewJobResponse::fromDetailed)
                .orElseThrow(() -> new ResourceNotFoundException("Review job with ID " + id + " was not found."));
    }

    @Override
    @Transactional
    public void deleteReview(String id) {
        ReviewJob job = reviewJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review job with ID " + id + " was not found."));
        reviewJobRepository.delete(job);
    }
}