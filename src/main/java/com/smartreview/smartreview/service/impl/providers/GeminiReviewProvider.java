package com.smartreview.smartreview.service.impl.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

@Slf4j
public class GeminiReviewProvider extends BaseReviewProvider {

    private final String apiUrl;
    private final String apiKey;
    private final String model;

    public GeminiReviewProvider(String apiUrl, String apiKey, String model) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    protected String callApi(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );

        String url = apiUrl.formatted(model, apiKey);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity(url, request, String.class).getBody();
    }

    @Override
    protected String extractText(String rawResponse) {
        try {
            return objectMapper.readTree(rawResponse)
                    .path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract text from Gemini response", e);
        }
    }
}