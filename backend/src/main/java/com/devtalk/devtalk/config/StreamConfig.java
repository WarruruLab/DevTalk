package com.devtalk.devtalk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class StreamConfig {

    @Bean
    public TaskExecutor taskExecutor(LlmProperties properties) {
        LlmProperties.Executor executor = properties.resolvedStream().resolvedExecutor();
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(executor.corePoolSize());
        ex.setMaxPoolSize(executor.maxPoolSize());
        ex.setQueueCapacity(executor.queueCapacity());
        ex.setThreadNamePrefix("ai-stream-");
        ex.initialize();
        return ex;
    }
}
