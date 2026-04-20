package com.smartreview.smartreview.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartreview.smartreview.model.FileReview;
import com.smartreview.smartreview.model.ReviewIssue;
import com.smartreview.smartreview.model.enums.IssueCategory;
import com.smartreview.smartreview.model.enums.Severity;
import com.smartreview.smartreview.service.CodeReviewProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OpenAIReviewProvider implements CodeReviewProvider {

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public FileReview review(String filePath, String language, String content) {
        log.debug("Reviewing file: {}", filePath);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "user", "content", buildPrompt(filePath, language, content))
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);

        return parseResponse(filePath, language, response.getBody());
    }

    private String buildPrompt(String filePath, String language, String content) {
        return """
                You are a senior software engineer performing a thorough code review.
                Analyse the following %s file and respond ONLY with valid JSON — no markdown, no explanation, just the JSON object.
 
                File: %s
 
                Your response must follow this exact schema:
                {
                  "summary": "brief overall assessment of the file (2-3 sentences)",
                  "score": <integer 0-100 representing code quality>,
                  "issues": [
                    {
                      "category": "<BUG | SECURITY | STYLE | SUGGESTION>",
                      "severity": "<HIGH | MEDIUM | LOW>",
                      "lineNumber": <integer or null>,
                      "description": "clear description of the issue",
                      "suggestedFix": "concrete suggestion to fix it"
                    }
                  ]
                }
 
                Scoring guide:
                - 90-100: excellent, production-ready
                - 70-89:  good, minor improvements needed
                - 50-69:  average, several issues to address
                - 30-49:  poor, significant problems
                - 0-29:   critical issues, major rework needed
 
                Focus on:
                - BUG: logic errors, null pointer risks, off-by-one errors, incorrect error handling
                - SECURITY: SQL injection, XSS, hardcoded secrets, insecure deserialization, missing auth checks
                - STYLE: naming conventions, code duplication, overly complex methods, missing documentation
                - SUGGESTION: performance improvements, better patterns, modern language features
 
                Code to review:
                ```%s
                %s
                ```
                """.formatted(language, filePath, language.toLowerCase(), content);
    }

    private FileReview parseResponse(String filePath, String language, String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String text = root.path("choices").get(0).path("message").path("content").asText();

            String cleaned = text.replaceAll("```json", "").replaceAll("```", "").trim();
            JsonNode result = objectMapper.readTree(cleaned);

            FileReview fileReview = FileReview.builder()
                    .filePath(filePath)
                    .language(language)
                    .summary(result.path("summary").asText("No summary provided"))
                    .fileScore(result.path("score").asInt(50))
                    .issues(new ArrayList<>())
                    .build();

            JsonNode issuesNode = result.path("issues");
            if (issuesNode.isArray()) {
                for (JsonNode issueNode : issuesNode) {
                    fileReview.getIssues().add(parseIssue(issueNode, fileReview));
                }
            }

            return fileReview;

        } catch (Exception e) {
            log.error("Failed to parse AI response for {}: {}", filePath, e.getMessage());
            return FileReview.builder()
                    .filePath(filePath)
                    .language(language)
                    .summary("Could not parse AI response for this file.")
                    .fileScore(0)
                    .issues(new ArrayList<>())
                    .build();
        }
    }

    private ReviewIssue parseIssue(JsonNode node, FileReview parent) {
        Integer lineNumber = null;
        JsonNode lineNode = node.path("lineNumber");
        if (!lineNode.isNull() && !lineNode.isMissingNode()) {
            lineNumber = lineNode.asInt();
        }

        return ReviewIssue.builder()
                .fileReview(parent)
                .category(parseCategory(node.path("category").asText()))
                .severity(parseSeverity(node.path("severity").asText()))
                .lineNumber(lineNumber)
                .description(node.path("description").asText("No description"))
                .suggestedFix(node.path("suggestedFix").asText(""))
                .build();
    }

    private IssueCategory parseCategory(String value) {
        try { return IssueCategory.valueOf(value.toUpperCase()); }
        catch (Exception e) { return IssueCategory.SUGGESTION; }
    }

    private Severity parseSeverity(String value) {
        try { return Severity.valueOf(value.toUpperCase()); }
        catch (Exception e) { return Severity.LOW; }
    }
}
