package com.smartreview.smartreview.service;

import com.smartreview.smartreview.model.FileReview;

public interface CodeReviewProvider {
    FileReview review(String filePath, String language, String content);
}
