package com.smartreview.smartreview.service.impl.providers;

import com.smartreview.smartreview.service.CodeReviewProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReviewProviderFactory {

    @Value("${groq.api.key:}")
    private String groqKey;

    @Value("${anthropic.api.key:}")
    private String anthropicKey;

    @Value("${openai.api.key:}")
    private String openaiKey;

    @Value("${gemini.api.key:}")
    private String geminiKey;

    public CodeReviewProvider create(String providerName, String userApiKey) {
        return switch (providerName.toUpperCase()) {
            case "GROQ"      -> new GroqReviewProvider(
                    resolveKey(userApiKey, groqKey), "llama-3.3-70b-versatile");
            case "ANTHROPIC" -> new AnthropicReviewProvider(
                    resolveKey(userApiKey, anthropicKey), "claude-haiku-4-5");
            case "OPENAI"    -> new OpenAiReviewProvider(
                    resolveKey(userApiKey, openaiKey), "gpt-4o-mini");
            case "GEMINI"    -> new GeminiReviewProvider(
                    resolveKey(userApiKey, geminiKey), "gemini-1.5-flash");
            default -> throw new IllegalArgumentException("Unknown provider: " + providerName);
        };
    }

    private String resolveKey(String userApiKey, String systemKey) {
        return (userApiKey != null && !userApiKey.isBlank()) ? userApiKey : systemKey;
    }
}