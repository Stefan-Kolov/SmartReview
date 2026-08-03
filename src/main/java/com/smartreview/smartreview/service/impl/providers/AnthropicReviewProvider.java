package com.smartreview.smartreview.service.impl.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

@Slf4j
public class AnthropicReviewProvider extends BaseReviewProvider {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private final String apiKey;
    private final String model;

    public AnthropicReviewProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    protected String callApi(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 2048,
                "system", "You are a senior software engineer. Respond ONLY with valid JSON — no markdown, no explanation.",
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity(API_URL, request, String.class).getBody();
    }

    @Override
    protected String extractText(String rawResponse) {
        try {
            return objectMapper.readTree(rawResponse)
                    .path("content").get(0)
                    .path("text").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract text from Anthropic response", e);
        }
    }
}