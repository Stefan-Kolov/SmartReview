package com.smartreview.smartreview.service.impl.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.List;
import java.util.Map;

@Slf4j
public class GroqReviewProvider extends BaseReviewProvider {

    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private final String apiKey;
    private final String model;

    public GroqReviewProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    protected String callApi(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        return callWithRetry(headers, body);
    }

    @Override
    protected String extractText(String rawResponse) {
        try {
            return objectMapper.readTree(rawResponse)
                    .path("choices").get(0)
                    .path("message").path("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract text from Groq response", e);
        }
    }

    private String callWithRetry(HttpHeaders headers, Map<String, Object> body) {
        int attempts = 0;
        int maxRetries = 5;
        while (true) {
            try {
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
                return restTemplate.postForEntity(API_URL, request, String.class).getBody();
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode().value() == 429 && ++attempts < maxRetries) {
                    log.warn("Rate limit hit. Attempt {}/{}. Waiting 5s...", attempts, maxRetries);
                    try { Thread.sleep(5000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else throw e;
            }
        }
    }
}