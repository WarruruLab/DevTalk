package com.devtalk.devtalk.config;

import com.devtalk.devtalk.service.llm.context.LlmPromptComposer;
import com.devtalk.devtalk.domain.llm.context.DefaultTailSelector;
import com.devtalk.devtalk.domain.llm.context.SessionSummaryStore;
import com.devtalk.devtalk.domain.llm.context.SummaryPolicy;
import com.devtalk.devtalk.domain.llm.context.TailSelector;
import com.devtalk.devtalk.domain.llm.context.TailSelectorPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiContextConfig {
    @Bean
    public TailSelectorPolicy tailSelectorPolicy(LlmProperties properties) {
        LlmProperties.Tail tail = properties.resolvedContext().resolvedTail();
        return new TailSelectorPolicy(tail.maxMessages(), tail.maxChars());
    }

    @Bean
    public TailSelector tailSelector(TailSelectorPolicy policy) {
        return new DefaultTailSelector(policy);
    }

    @Bean
    public SummaryPolicy summaryPolicy(LlmProperties properties) {
        LlmProperties.Summary summary = properties.resolvedContext().resolvedSummary();
        return new SummaryPolicy(
            summary.promptMaxChars(),
            summary.hardMaxChars(),
            summary.keepTailMessages()
        );
    }

//    @Bean
//    public SessionSummaryStore sessionSummaryStore() {
//        return new InMemorySessionSummaryStore();
//    }

    @Bean
    public LlmPromptComposer llmPromptComposer() {
        return new LlmPromptComposer();
    }
}
