package com.devtalk.devtalk.infra.llm;

import com.devtalk.devtalk.domain.llm.LlmFailureCode;
import com.devtalk.devtalk.domain.llm.LlmFinishReason;
import com.devtalk.devtalk.domain.llm.LlmRequest;
import com.devtalk.devtalk.domain.llm.LlmStreamClient;
import com.devtalk.devtalk.domain.llm.LlmStreamEvent;
import com.devtalk.devtalk.domain.llm.LlmTokenUsage;
import java.time.Instant;
import reactor.core.publisher.Flux;

public final class MockLlmStreamClient implements LlmStreamClient {

    private final boolean alwaysFail;

    public MockLlmStreamClient(boolean alwaysFail) {
        this.alwaysFail = alwaysFail;
    }

    @Override
    public Flux<LlmStreamEvent> stream(LlmRequest request) {
        if (alwaysFail) {
            return Flux.error(new IllegalStateException(
                LlmFailureCode.PROVIDER_ERROR + ": Mock stream failure"
            ));
        }

        String text = "(MOCK AI STREAM)\n"
            + "now=" + Instant.now() + "\n"
            + "messages=" + request.messages().size();

        return Flux.just(
            LlmStreamEvent.delta(text),
            LlmStreamEvent.finish(LlmFinishReason.STOP, LlmTokenUsage.empty())
        );
    }
}
