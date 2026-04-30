package com.devtalk.devtalk.infra.llm;

import com.devtalk.devtalk.domain.llm.LlmFinishReason;
import com.devtalk.devtalk.domain.llm.LlmOptions;
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
public final class GeminiStreamClient implements LlmStreamClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final LlmOptions defaultOptions;

    public GeminiStreamClient(WebClient webClient, ObjectMapper objectMapper, String apiKey, String model, LlmOptions defaultOptions) {
        this.webClient = Objects.requireNonNull(webClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.apiKey = Objects.requireNonNull(apiKey);
        this.model = Objects.requireNonNull(model);
        this.defaultOptions = Objects.requireNonNull(defaultOptions);
    }

    @Override
    public Flux<LlmStreamEvent> stream(LlmRequest request) {
        GeminiHttpClient.GeminiGenerateRequest payload = GeminiHttpClient.GeminiGenerateRequest.from(request, defaultOptions);

        Flux<String> chunks = webClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/v1beta/models/{model}:streamGenerateContent")
                .queryParam("key", apiKey)
                .build(model))
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .exchangeToFlux(resp -> resp.bodyToFlux(String.class));

        return parseGeminiJsonChunksToEvents(chunks);
    }

    private Flux<LlmStreamEvent> parseGeminiJsonChunksToEvents(Flux<String> chunks) {
        return Flux.create(sink -> {
            StringBuilder buf = new StringBuilder();

            chunks.subscribe(
                chunk -> {
                    if (chunk == null || chunk.isEmpty()) return;
                    buf.append(chunk);

                    try {
                        JsonNode root = objectMapper.readTree(buf.toString());

                        for (LlmStreamEvent ev : extractEvents(root)) {
                            sink.next(ev);
                        }

                        buf.setLength(0);
                    } catch (Exception notYet) {
                        // 아직 JSON이 완성되지 않음 -> 계속 누적
                    }
                },
                sink::error,
                sink::complete
            );
        });
    }

    private List<LlmStreamEvent> extractEvents(JsonNode root) {
        List<JsonNode> items = new ArrayList<>();
        if (root.isArray()) root.forEach(items::add);
        else items.add(root);

        List<LlmStreamEvent> out = new ArrayList<>();

        for (JsonNode item : items) {
            JsonNode c0 = item.path("candidates").path(0);

            // delta
            JsonNode parts = c0.path("content").path("parts");
            if (parts.isArray()) {
                for (JsonNode p : parts) {
                    String text = p.path("text").asText("");
                    if (!text.isEmpty()) out.add(LlmStreamEvent.delta(text));
                }
            }

            // done
            String fr = c0.path("finishReason").asText("");
            if (!fr.isEmpty()) out.add(LlmStreamEvent.done(toFinishReason(fr)));
        }

        return out;
    }

    private LlmFinishReason toFinishReason(String fr) {
        try {
            return LlmFinishReason.valueOf(fr);
        } catch (Exception e) {
            return LlmFinishReason.UNKNOWN;
        }
    }
}
