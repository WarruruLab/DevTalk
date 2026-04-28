package com.devtalk.devtalk.config;

import com.devtalk.devtalk.domain.llm.LlmClient;
import com.devtalk.devtalk.domain.llm.LlmStreamClient;
import com.devtalk.devtalk.infra.llm.GeminiHttpClient;
import com.devtalk.devtalk.infra.llm.GeminiStreamClient;
import com.devtalk.devtalk.infra.llm.MockLlmClient;
import com.devtalk.devtalk.infra.llm.MockLlmStreamClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
    public LlmClient llmClient(
        RestClient geminiRestClient,
        LlmProperties properties
    ) {
        LlmProperties.Gemini gemini = properties.resolvedGemini();
        return switch (properties.resolvedMode().toLowerCase()) {
            case "gemini" -> new GeminiHttpClient(geminiRestClient, gemini.apiKey(), gemini.model());
            case "mock" -> new MockLlmClient(properties.resolvedMock().alwaysFail());
            case "ollama" -> throw new IllegalStateException("LLM_MODE=ollama is not implemented yet");
            default -> throw new IllegalArgumentException("Unsupported LLM mode: " + properties.resolvedMode());
        };
    }

    @Bean
    public LlmStreamClient llmStreamClient(
        ObjectMapper objectMapper,
        LlmProperties properties
    ) {
        LlmProperties.Gemini gemini = properties.resolvedGemini();
        return switch (properties.resolvedMode().toLowerCase()) {
            case "gemini" -> new GeminiStreamClient(geminiWebClient(gemini), objectMapper, gemini.apiKey(), gemini.model());
            case "mock" -> new MockLlmStreamClient(properties.resolvedMock().alwaysFail());
            case "ollama" -> throw new IllegalStateException("LLM_MODE=ollama is not implemented yet");
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
}
