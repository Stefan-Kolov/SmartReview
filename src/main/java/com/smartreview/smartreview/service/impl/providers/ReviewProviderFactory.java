package com.smartreview.smartreview.service.impl.providers;

import com.smartreview.smartreview.service.CodeReviewProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReviewProviderFactory {

    @Value("${groq.api.key:}")
    private String groqKey;
    @Value("${groq.api.url}")
    private String groqUrl;
    @Value("${groq.model}")
    private String groqModel;

    @Value("${anthropic.api.key:}")
    private String anthropicKey;
    @Value("${anthropic.api.url}")
    private String anthropicUrl;
    @Value("${anthropic.model}")
    private String anthropicModel;

    @Value("${openai.api.key:}")
    private String openaiKey;
    @Value("${openai.api.url}")
    private String openaiUrl;
    @Value("${openai.model}")
    private String openaiModel;

    @Value("${gemini.api.key:}")
    private String geminiKey;
    @Value("${gemini.api.url}")
    private String geminiUrl;
    @Value("${gemini.model}")
    private String geminiModel;

    public CodeReviewProvider create(String providerName, String userApiKey) {
        return switch (providerName.toUpperCase()) {
            case "GROQ"      -> new GroqReviewProvider(
                    resolveKey(userApiKey, groqKey), groqUrl, groqModel);
            case "ANTHROPIC" -> new AnthropicReviewProvider(
                    resolveKey(userApiKey, anthropicKey), anthropicUrl, anthropicModel);
            case "OPENAI"    -> new OpenAiReviewProvider(
                    resolveKey(userApiKey, openaiKey), openaiUrl, openaiModel);
            case "GEMINI"    -> new GeminiReviewProvider(
                    resolveKey(userApiKey, geminiKey), geminiUrl, geminiModel);
            default -> throw new IllegalArgumentException("Unknown provider: " + providerName);
        };
    }

    private String resolveKey(String userApiKey, String systemKey) {
        return (userApiKey != null && !userApiKey.isBlank()) ? userApiKey : systemKey;
    }
}