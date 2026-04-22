package com.smartreview.smartreview.service;

import com.smartreview.smartreview.model.FileReview;
import com.smartreview.smartreview.service.impl.RepoService;

import java.util.List;
import java.util.Map;

public interface CodeReviewProvider {
    FileReview review(String filePath, String language, String content);
    List<FileReview> reviewBatch(List<Map.Entry<String, String>> files, RepoService repoService);
}
