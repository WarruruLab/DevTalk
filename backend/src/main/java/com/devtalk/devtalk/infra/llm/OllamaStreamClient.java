package com.devtalk.devtalk.infra.llm;

import com.devtalk.devtalk.domain.llm.LlmFinishReason;
import com.devtalk.devtalk.domain.llm.LlmRequest;
import com.devtalk.devtalk.domain.llm.LlmStreamClient;
import com.devtalk.devtalk.domain.llm.LlmStreamEvent;
import java.util.ArrayList;
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
    private final ObjectMapper objectMapper;
    private final String model;

    public OllamaStreamClient(WebClient webClient, ObjectMapper objectMapper, String model) {
        this.webClient = Objects.requireNonNull(webClient, "webClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.model = Objects.requireNonNull(model, "model must not be null");
    }

    @Override
    public Flux<LlmStreamEvent> stream(LlmRequest request) {
        OllamaHttpClient.OllamaChatRequest payload = OllamaHttpClient.OllamaChatRequest.from(request, model, true);

        Flux<String> chunks = webClient.post()
            .uri("/api/chat")
            .accept(APPLICATION_NDJSON, MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .bodyToFlux(String.class);

        return parseNdjsonToEvents(chunks);
    }

    private Flux<LlmStreamEvent> parseNdjsonToEvents(Flux<String> chunks) {
        return Flux.create(sink -> {
            StringBuilder buffer = new StringBuilder();

            chunks.subscribe(
                chunk -> {
                    if (chunk == null || chunk.isEmpty()) return;
                    buffer.append(chunk);
                    try {
                        emitCompleteLines(buffer).forEach(sink::next);
                    } catch (Exception e) {
                        sink.error(e);
                    }
                },
                sink::error,
                () -> {
                    String remaining = buffer.toString().trim();
                    if (!remaining.isEmpty()) {
                        try {
                            extractEvents(objectMapper.readTree(remaining)).forEach(sink::next);
                        } catch (Exception e) {
                            sink.error(e);
                            return;
                        }
                    }
                    sink.complete();
                }
            );
        });
    }

    private List<LlmStreamEvent> emitCompleteLines(StringBuilder buffer) {
        List<LlmStreamEvent> events = new ArrayList<>();
        int newlineIndex;
        while ((newlineIndex = indexOfNewline(buffer)) >= 0) {
            String line = buffer.substring(0, newlineIndex).trim();
            int nextStart = newlineIndex + 1;
            if (newlineIndex > 0 && buffer.charAt(newlineIndex - 1) == '\r') {
                line = buffer.substring(0, newlineIndex - 1).trim();
            }
            buffer.delete(0, nextStart);

            if (line.isEmpty()) continue;
            try {
                events.addAll(extractEvents(objectMapper.readTree(line)));
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse Ollama stream chunk", e);
            }
        }
        return events;
    }

    private List<LlmStreamEvent> extractEvents(JsonNode root) {
        List<LlmStreamEvent> events = new ArrayList<>();

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

    private int indexOfNewline(StringBuilder buffer) {
        for (int i = 0; i < buffer.length(); i++) {
            if (buffer.charAt(i) == '\n') return i;
        }
        return -1;
    }
}
