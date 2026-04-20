package com.smartreview.smartreview.web;

import com.smartreview.smartreview.model.dto.ReviewJobResponse;
import com.smartreview.smartreview.model.dto.ReviewRequest;
import com.smartreview.smartreview.model.ReviewJob;
import com.smartreview.smartreview.repository.ReviewJobRepository;
import com.smartreview.smartreview.service.impl.ReviewOrchestrator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReviewController {

    private final ReviewOrchestrator reviewOrchestrator;
    private final ReviewJobRepository reviewJobRepository;

    @PostMapping
    public ResponseEntity<ReviewJobResponse> submitReview(@Valid @RequestBody ReviewRequest request) {
        log.info("Received review request for: {}", request.getRepoUrl());
        ReviewJob job = reviewOrchestrator.startReview(request.getRepoUrl());
        return ResponseEntity.ok(ReviewJobResponse.from(job));
    }

    @GetMapping
    public ResponseEntity<List<ReviewJobResponse>> listReviews() {
        List<ReviewJobResponse> reviews = reviewJobRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(ReviewJobResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReviewJobResponse> getReview(@PathVariable String id) {
        return reviewJobRepository.findById(id)
                .map(job -> ResponseEntity.ok(ReviewJobResponse.fromDetailed(job)))
                .orElse(ResponseEntity.notFound().build());
    }
}