package com.devtalk.devtalk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
    String mode,
    Mock mock,
    Gemini gemini,
    Ollama ollama
) {
    public String resolvedMode() {
        return (mode == null || mode.isBlank()) ? "mock" : mode;
    }

    public Mock resolvedMock() {
        return mock == null ? new Mock(false) : mock;
    }

    public Gemini resolvedGemini() {
        return gemini == null ? new Gemini(
            "https://generativelanguage.googleapis.com",
            "",
            "gemini-2.5-flash",
            3000,
            15000,
            60000
        ) : gemini;
    }

    public Ollama resolvedOllama() {
        return ollama == null ? new Ollama(
            "http://ollama:11434",
            "qwen2.5:3b",
            3000,
            60000,
            120000
        ) : ollama;
    }

    public record Mock(boolean alwaysFail) {}

    public record Gemini(
        String baseUrl,
        String apiKey,
        String model,
        long connectTimeoutMs,
        long readTimeoutMs,
        long streamResponseTimeoutMs
    ) {}

    public record Ollama(
        String baseUrl,
        String model,
        long connectTimeoutMs,
        long readTimeoutMs,
        long streamResponseTimeoutMs
    ) {}
}
