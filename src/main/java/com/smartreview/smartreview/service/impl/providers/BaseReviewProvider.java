package com.smartreview.smartreview.service.impl.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartreview.smartreview.model.FileReview;
import com.smartreview.smartreview.model.ReviewIssue;
import com.smartreview.smartreview.model.enums.IssueCategory;
import com.smartreview.smartreview.model.enums.Severity;
import com.smartreview.smartreview.service.CodeReviewProvider;
import com.smartreview.smartreview.service.impl.RepoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class BaseReviewProvider implements CodeReviewProvider {

    protected final RestTemplate restTemplate = new RestTemplate();
    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected abstract String callApi(String prompt);
    protected abstract String extractText(String rawResponse);

    @Override
    public FileReview review(String filePath, String language, String content) {
        log.debug("Reviewing file: {}", filePath);
        String prompt = buildPrompt(filePath, language, content);
        String rawResponse = callApi(prompt);
        return parseResponse(filePath, language, rawResponse);
    }

    @Override
    public List<FileReview> reviewBatch(List<Map.Entry<String, String>> files, RepoService repoService) {
        return files.stream()
                .map(entry -> review(
                        entry.getKey(),
                        repoService.detectLanguage(entry.getKey()),
                        entry.getValue()))
                .toList();
    }

    protected String buildPrompt(String filePath, String language, String content) {
        String numberedContent = numberLines(content);

        return """
                You are a senior software engineer performing a thorough code review.
                Analyse the following %s file and respond ONLY with valid JSON — no markdown, no explanation, just the JSON object.

                File: %s
                
                The code below has each line prefixed with its exact line number followed by a colon,
                e.g. "42: private String name;". When you report an issue, copy the line number exactly
                as shown in that prefix — do NOT count lines yourself, use the given number.

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

                Code to review (line numbers included, do not include them in suggestedFix):
                %s
                """.formatted(language, filePath, numberedContent);
    }

    private String numberLines(String content) {
        String[] lines = content.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(i + 1).append(": ").append(lines[i]).append("\n");
        }
        return sb.toString();
    }

    protected FileReview parseResponse(String filePath, String language, String rawResponse) {
        try {
            String text = extractText(rawResponse);
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