package com.devtalk.devtalk.infra.llm;

import com.devtalk.devtalk.domain.llm.LlmClient;
import com.devtalk.devtalk.domain.llm.LlmFailureCode;
import com.devtalk.devtalk.domain.llm.LlmFinishReason;
import com.devtalk.devtalk.domain.llm.LlmMessage;
import com.devtalk.devtalk.domain.llm.LlmOptions;
import com.devtalk.devtalk.domain.llm.LlmRequest;
import com.devtalk.devtalk.domain.llm.LlmResult;
import com.devtalk.devtalk.domain.llm.LlmRole;
import com.devtalk.devtalk.domain.llm.LlmTokenUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

public final class OllamaHttpClient implements LlmClient {

    private final RestClient restClient;
    private final String model;
    private final LlmOptions defaultOptions;

    public OllamaHttpClient(RestClient restClient, String model, LlmOptions defaultOptions) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        this.model = Objects.requireNonNull(model, "model must not be null");
        this.defaultOptions = Objects.requireNonNull(defaultOptions, "defaultOptions must not be null");
    }

    @Override
    public LlmResult generate(LlmRequest request) {
        try {
            OllamaChatRequest payload = OllamaChatRequest.from(request, model, false, defaultOptions);

            JsonNode response = restClient.post()
                .uri("/api/chat")
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

            if (response == null) {
                return LlmResult.Failure.of(LlmFailureCode.PROVIDER_ERROR, "Empty response from Ollama");
            }

            String text = extractText(response);
            if (text == null || text.isBlank()) {
                return LlmResult.Failure.of(
                    LlmFailureCode.PROVIDER_ERROR,
                    "Ollama returned no text",
                    safeDebug(response)
                );
            }

            return LlmResult.Success.of(
                text,
                mapFinishReason(response.path("done_reason").asText("")),
                toTokenUsage(response)
            );
        } catch (ResourceAccessException e) {
            return LlmResult.Failure.of(LlmFailureCode.NETWORK_ERROR, "Network/timeout error", e.getMessage());
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            String detail = "status=%d body=%s".formatted(status.value(), safeBody(e));
            return LlmResult.Failure.of(mapStatusToFailureCode(status), "Ollama request failed", detail);
        } catch (Exception e) {
            return LlmResult.Failure.of(LlmFailureCode.UNKNOWN, "Unexpected error", e.getMessage());
        }
    }

    public static RestClient buildRestClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeout.toMillis());
        factory.setReadTimeout((int) readTimeout.toMillis());

        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .build();
    }

    static LlmFinishReason mapFinishReason(String raw) {
        if (raw == null || raw.isBlank()) return LlmFinishReason.UNKNOWN;

        return switch (raw.toLowerCase()) {
            case "stop" -> LlmFinishReason.STOP;
            case "length" -> LlmFinishReason.MAX_TOKENS;
            default -> LlmFinishReason.OTHER;
        };
    }

    private static LlmFailureCode mapStatusToFailureCode(HttpStatusCode status) {
        int value = status.value();
        if (value >= 400 && value < 500) return LlmFailureCode.INVALID_REQUEST;
        if (value >= 500) return LlmFailureCode.PROVIDER_ERROR;
        return LlmFailureCode.UNKNOWN;
    }

    private static String safeBody(RestClientResponseException e) {
        try {
            String body = e.getResponseBodyAsString();
            return (body == null || body.isBlank()) ? "(empty)" : body;
        } catch (Exception ex) {
            return "(unavailable)";
        }
    }

    static String extractText(JsonNode response) {
        if (response == null) return null;
        String text = response.path("message").path("content").asText("");
        text = text.trim();
        return text.isBlank() ? null : text;
    }

    static LlmTokenUsage toTokenUsage(JsonNode response) {
        if (response == null) return LlmTokenUsage.empty();
        return new LlmTokenUsage(
            response.path("prompt_eval_count").asInt(0),
            response.path("eval_count").asInt(0)
        );
    }

    private static String safeDebug(JsonNode response) {
        if (response == null) return "(response unavailable)";
        return "done=%s done_reason=%s".formatted(
            response.path("done").asText(""),
            response.path("done_reason").asText("")
        );
    }

    public record OllamaChatRequest(
        String model,
        boolean stream,
        List<Message> messages,
        Map<String, Object> options
    ) {
        public record Message(String role, String content) {}

        public static OllamaChatRequest from(LlmRequest request, String model, boolean stream, LlmOptions defaultOptions) {
            List<Message> messages = new ArrayList<>();
            if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
                messages.add(new Message("system", request.systemPrompt()));
            }

            for (LlmMessage message : request.messages()) {
                messages.add(new Message(mapRole(message.role()), message.content()));
            }

            LlmOptions options = request.options() == null ? defaultOptions : request.options();
            Map<String, Object> ollamaOptions = new LinkedHashMap<>();
            if (options.temperature() != null) ollamaOptions.put("temperature", options.temperature());
            if (options.maxTokens() != null) ollamaOptions.put("num_predict", options.maxTokens());

            return new OllamaChatRequest(model, stream, messages, ollamaOptions);
        }

        private static String mapRole(LlmRole role) {
            return switch (role) {
                case SYSTEM -> "system";
                case USER -> "user";
                case AI -> "assistant";
            };
        }
    }
}
