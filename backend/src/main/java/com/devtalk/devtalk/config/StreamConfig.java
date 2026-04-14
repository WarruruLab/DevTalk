package com.devtalk.devtalk.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class StreamConfig {

    @Bean
    public WebClient geminiWebClient(
        @Value("${llm.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
        @Value("${llm.gemini.stream-response-timeout-ms:60000}") long streamResponseTimeoutMs
    ) {
        HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofMillis(streamResponseTimeoutMs));

        return WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("ai-stream-");
        ex.initialize();
        return ex;
    }
}
