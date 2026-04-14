package com.devtalk.devtalk.config;

import com.devtalk.devtalk.infra.llm.GeminiHttpClient;
import com.devtalk.devtalk.infra.llm.GeminiStreamClient;
import com.devtalk.devtalk.infra.llm.MockLlmClient;
import com.devtalk.devtalk.domain.llm.LlmClient;
import com.devtalk.devtalk.domain.llm.LlmStreamClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class LlmConfig {

    @Bean
    public RestClient geminiRestClient(
        @Value("${llm.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
        @Value("${llm.gemini.connect-timeout-ms:3000}") long connectTimeoutMs,
        @Value("${llm.gemini.read-timeout-ms:15000}") long readTimeoutMs
    ) {
        return GeminiHttpClient.buildRestClient(
            baseUrl,
            Duration.ofMillis(connectTimeoutMs),
            Duration.ofMillis(readTimeoutMs)
        );
    }

    @Bean
    public LlmClient llmClient(
        RestClient geminiRestClient,
        @Value("${llm.mode:mock}") String mode,
        @Value("${llm.gemini.api-key:}") String apiKey,
        @Value("${llm.gemini.model:gemini-2.5-flash}") String model,
        @Value("${llm.mock.always-fail:false}") boolean mockAlwaysFail
    ) {
        if ("gemini".equalsIgnoreCase(mode)) {
            return new GeminiHttpClient(geminiRestClient, apiKey, model);
        }
        return new MockLlmClient(mockAlwaysFail);
    }

    @Bean
    public LlmStreamClient llmStreamClient(
        WebClient geminiWebClient,
        ObjectMapper objectMapper,
        @Value("${llm.gemini.api-key:}") String apiKey,
        @Value("${llm.gemini.model:gemini-2.5-flash}") String model
    ) {
        return new GeminiStreamClient(geminiWebClient, objectMapper, apiKey, model);
    }
}
