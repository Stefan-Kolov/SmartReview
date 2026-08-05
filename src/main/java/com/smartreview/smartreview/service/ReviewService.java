package com.smartreview.smartreview.service;

import com.smartreview.smartreview.model.User;
import com.smartreview.smartreview.model.dto.ReviewJobResponse;
import com.smartreview.smartreview.model.dto.ReviewRequest;

import java.util.List;

public interface ReviewService {
    ReviewJobResponse submitReview(ReviewRequest request, User user);
    List<ReviewJobResponse> listMyReviews(User user);
    ReviewJobResponse getReviewDetails(String id);
    void deleteReview(String id);
}