package com.devtalk.devtalk.infra.llm;

import com.devtalk.devtalk.domain.llm.LlmFinishReason;
import com.devtalk.devtalk.domain.llm.LlmRequest;
import com.devtalk.devtalk.domain.llm.LlmStreamClient;
import com.devtalk.devtalk.domain.llm.LlmStreamEvent;
import java.util.List;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class OllamaStreamClient implements LlmStreamClient {

    private static final MediaType APPLICATION_NDJSON = MediaType.parseMediaType("application/x-ndjson");

    private final WebClient webClient;
    private final String model;

    public OllamaStreamClient(WebClient webClient, ObjectMapper objectMapper, String model) {
        this.webClient = Objects.requireNonNull(webClient, "webClient must not be null");
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.model = Objects.requireNonNull(model, "model must not be null");
    }

    @Override
    public Flux<LlmStreamEvent> stream(LlmRequest request) {
        OllamaHttpClient.OllamaChatRequest payload = OllamaHttpClient.OllamaChatRequest.from(request, model, true);

        return webClient.post()
            .uri("/api/chat")
            .accept(APPLICATION_NDJSON, MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .bodyToFlux(JsonNode.class)
            .flatMapIterable(this::extractEvents);
    }

    private List<LlmStreamEvent> extractEvents(JsonNode root) {
        java.util.ArrayList<LlmStreamEvent> events = new java.util.ArrayList<>();

        String delta = root.path("message").path("content").asText("");
        if (!delta.isEmpty()) {
            events.add(LlmStreamEvent.delta(delta));
        }

        if (root.path("done").asBoolean(false)) {
            events.add(LlmStreamEvent.finish(
                OllamaHttpClient.mapFinishReason(root.path("done_reason").asText("")),
                OllamaHttpClient.toTokenUsage(root)
            ));
        }

        return events;
    }
}
