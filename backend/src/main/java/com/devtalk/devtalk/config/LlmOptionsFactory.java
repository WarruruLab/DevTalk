package com.devtalk.devtalk.config;

import com.devtalk.devtalk.domain.llm.LlmOptions;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class LlmOptionsFactory {
    private final LlmProperties properties;

    public LlmOptionsFactory(LlmProperties properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    public LlmOptions defaults() {
        LlmProperties.Default defaults = properties.resolvedDefaults();
        return new LlmOptions(defaults.temperature(), defaults.maxTokens());
    }
}
