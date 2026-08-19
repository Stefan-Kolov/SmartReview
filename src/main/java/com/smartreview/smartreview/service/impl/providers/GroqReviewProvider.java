package com.smartreview.smartreview.service.impl.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

@Slf4j
public class GroqReviewProvider extends BaseReviewProvider {

    private final String apiUrl;
    private final String apiKey;
    private final String model;

    public GroqReviewProvider(String apiKey, String apiUrl, String model) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
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

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity(apiUrl, request, String.class).getBody();
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
}