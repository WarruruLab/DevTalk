package com.devtalk.devtalk.config;

import com.devtalk.devtalk.domain.llm.LlmClient;
import com.devtalk.devtalk.domain.llm.LlmStreamClient;
import com.devtalk.devtalk.infra.llm.GeminiHttpClient;
import com.devtalk.devtalk.infra.llm.GeminiStreamClient;
import com.devtalk.devtalk.infra.llm.MockLlmClient;
import com.devtalk.devtalk.infra.llm.MockLlmStreamClient;
import com.devtalk.devtalk.infra.llm.OllamaHttpClient;
import com.devtalk.devtalk.infra.llm.OllamaStreamClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    @Bean
    public RestClient geminiRestClient(LlmProperties properties) {
        LlmProperties.Gemini gemini = properties.resolvedGemini();
        return GeminiHttpClient.buildRestClient(
            gemini.baseUrl(),
            Duration.ofMillis(gemini.connectTimeoutMs()),
            Duration.ofMillis(gemini.readTimeoutMs())
        );
    }

    @Bean
    public RestClient ollamaRestClient(LlmProperties properties) {
        LlmProperties.Ollama ollama = properties.resolvedOllama();
        return OllamaHttpClient.buildRestClient(
            ollama.baseUrl(),
            Duration.ofMillis(ollama.connectTimeoutMs()),
            Duration.ofMillis(ollama.readTimeoutMs())
        );
    }

    @Bean
    public LlmClient llmClient(
        @Qualifier("geminiRestClient") RestClient geminiRestClient,
        @Qualifier("ollamaRestClient") RestClient ollamaRestClient,
        LlmProperties properties
    ) {
        LlmProperties.Gemini gemini = properties.resolvedGemini();
        LlmProperties.Ollama ollama = properties.resolvedOllama();
        return switch (properties.resolvedMode().toLowerCase()) {
            case "gemini" -> new GeminiHttpClient(geminiRestClient, gemini.apiKey(), gemini.model());
            case "mock" -> new MockLlmClient(properties.resolvedMock().alwaysFail());
            case "ollama" -> new OllamaHttpClient(ollamaRestClient, ollama.model());
            default -> throw new IllegalArgumentException("Unsupported LLM mode: " + properties.resolvedMode());
        };
    }

    @Bean
    public LlmStreamClient llmStreamClient(
        ObjectMapper objectMapper,
        LlmProperties properties
    ) {
        LlmProperties.Gemini gemini = properties.resolvedGemini();
        LlmProperties.Ollama ollama = properties.resolvedOllama();
        return switch (properties.resolvedMode().toLowerCase()) {
            case "gemini" -> new GeminiStreamClient(geminiWebClient(gemini), objectMapper, gemini.apiKey(), gemini.model());
            case "mock" -> new MockLlmStreamClient(properties.resolvedMock().alwaysFail());
            case "ollama" -> new OllamaStreamClient(ollamaWebClient(ollama), objectMapper, ollama.model());
            default -> throw new IllegalArgumentException("Unsupported LLM mode: " + properties.resolvedMode());
        };
    }

    private WebClient geminiWebClient(LlmProperties.Gemini gemini) {
        HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofMillis(gemini.streamResponseTimeoutMs()));

        return WebClient.builder()
            .baseUrl(gemini.baseUrl())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    private WebClient ollamaWebClient(LlmProperties.Ollama ollama) {
        HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofMillis(ollama.streamResponseTimeoutMs()));

        return WebClient.builder()
            .baseUrl(ollama.baseUrl())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}
