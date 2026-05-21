package com.smartreview.smartreview.web;

import com.smartreview.smartreview.model.User;
import com.smartreview.smartreview.model.dto.ReviewJobResponse;
import com.smartreview.smartreview.model.dto.ReviewRequest;
import com.smartreview.smartreview.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReviewController {
    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ReviewJobResponse> submitReview(
            @Valid @RequestBody ReviewRequest request,
            @AuthenticationPrincipal User user) {

        log.info("Received review request from user: {} for: {}", user.getUsername(), request.getRepoUrl());
        return ResponseEntity.ok(reviewService.submitReview(request, user));
    }

    @GetMapping
    public ResponseEntity<List<ReviewJobResponse>> listReviews(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(reviewService.listMyReviews(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReviewJobResponse> getReview(@PathVariable String id) {
        try {
            return ResponseEntity.ok(reviewService.getReviewDetails(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}