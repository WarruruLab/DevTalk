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
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// infra에서 구현체를 구현해 계층을 분리 시켰기 때문에 따로 Gemini용 중첩 클래스를 활용해 구현
// 하나의 어댑터의 역할로 service는 LlmClient만 알기 때문에 infra에서 gemini에 맞게 변경해 요청하는 역할
public class GeminiHttpClient implements LlmClient {

    private static final ObjectMapper om = new ObjectMapper();

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final LlmOptions defaultOptions;

    public GeminiHttpClient(RestClient restClient, String apiKey, String model, LlmOptions defaultOptions) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
        this.model = Objects.requireNonNull(model, "model must not be null");
        this.defaultOptions = Objects.requireNonNull(defaultOptions, "defaultOptions must not be null");
    }
    // 인터페이스의 함수를 오버라이딩 하여 적용
    // LlmRequest를 Gemini에 맞는 GeminiGenerateRequest로 변환
    // 변환된 GeminiGenerateRequest로 Post 호출한뒤에 응답 추출
    // 성공시에 Success로 리턴
    // 실패시에 Failure로 리턴
    @Override
    public LlmResult generate(LlmRequest request) {
        // try catch를 통해 성공과 실패 모두 기록으로 남길 수 있도록 반환
        try {
            // LlmRequest를 Gemini에 맞게 변환
            GeminiGenerateRequest payload = GeminiGenerateRequest.from(request, defaultOptions);
            // post 요청으로 응답을 받음
            GeminiGenerateResponse response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path("/v1beta/models/{model}:generateContent")
                    .queryParam("key", apiKey)
                    .build(model))
                .body(payload)
                .retrieve()
                .body(GeminiGenerateResponse.class);
            // null일 경우 Fail이기 때문에 따로처리를 해줌 => 실패도 기록한다.
            if (response == null) {
                return LlmResult.Failure.of(LlmFailureCode.PROVIDER_ERROR, "Empty response from Gemini");
            }
            // 응답에서 Text를 추출해 성공과 실패로 반환
            String text = response.extractFirstText();
            LlmFinishReason finishReason = mapFinishReason(response.firstFinishReasonRaw());
            LlmTokenUsage tokenUsage = response.toTokenUsage();
            if (text == null || text.isBlank()) {
                return LlmResult.Failure.of(
                    LlmFailureCode.PROVIDER_ERROR,
                    "Gemini returned no text",
                    response.safeDebug()
                );
            }

            return LlmResult.Success.of(text, finishReason, tokenUsage);

        } catch (ResourceAccessException e) {
            // 타임아웃/연결 실패 계열 처리
            return LlmResult.Failure.of(LlmFailureCode.NETWORK_ERROR, "Network/timeout error", e.getMessage());

        } catch (RestClientResponseException e) {
            // 4xx/5xx + response body
            HttpStatusCode status = e.getStatusCode();
            LlmFailureCode code = mapStatusToFailureCode(status);

            String detail = "status=%d body=%s".formatted(
                status.value(),
                safeBody(e)
            );

            return LlmResult.Failure.of(code, "Gemini request failed", detail);

        } catch (Exception e) {
            return LlmResult.Failure.of(LlmFailureCode.UNKNOWN, "Unexpected error", e.getMessage());
        }
    }
    // HTTP 헤더를 통해서 미리 정의한 enum 대로 리턴
    private static LlmFailureCode mapStatusToFailureCode(HttpStatusCode status) {
        int s = status.value();
        if (s == 401 || s == 403) return LlmFailureCode.AUTH_ERROR;
        if (s == 429) return LlmFailureCode.RATE_LIMIT;
        if (s >= 400 && s < 500) return LlmFailureCode.INVALID_REQUEST;
        if (s >= 500) return LlmFailureCode.PROVIDER_ERROR;
        return LlmFailureCode.UNKNOWN;
    }
    // 4xx, 5xx의 오류를 문자열로 변환하기 위한 함수
    private static String safeBody(RestClientResponseException e) {
        try {
            String body = e.getResponseBodyAsString();
            return (body == null || body.isBlank()) ? "(empty)" : body;
        } catch (Exception ex) {
            return "(unavailable)";
        }
    }
    // Gemini에만 사용하기 때문에 중첩 클래스를 사용

    /**
     * Gemini 요청 정의
     *
     * - systemInstruction: 시스템 프롬프트
     * - contents: 대화 메시지들 (role + parts[text])
     * - generationConfig: temperature, maxOutputTokens
     */
    public record GeminiGenerateRequest(
        Content systemInstruction,
        List<Content> contents,
        GenerationConfig generationConfig
    ) {
        public record Content(String role, List<Part> parts) {}
        public record Part(String text) {}
        public record GenerationConfig(Double temperature, Integer maxOutputTokens) {}

        public static GeminiGenerateRequest from(LlmRequest request, LlmOptions defaultOptions) {
            Content systemInstruction = null;
            if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
                systemInstruction = new Content(null, List.of(new Part(request.systemPrompt())));
            }

            List<Content> contents = new ArrayList<>();
            for (LlmMessage m : request.messages()) {
                if (m.role() == LlmRole.SYSTEM) continue;
                String role = switch (m.role()) {
                    case USER -> "user";
                    case AI -> "model";
                    default -> "user";
                };
                contents.add(new Content(role, List.of(new Part(m.content()))));
            }

            LlmOptions opt = (request.options() != null) ? request.options() : defaultOptions;

            return new GeminiGenerateRequest(
                systemInstruction,
                contents,
                new GenerationConfig(opt.temperature(), opt.maxTokens())
            );
        }
    }

    /**
     * Gemini generateContent 응답
     * candidates[0].content.parts[0].text 를 주로 사용
     */
    public record GeminiGenerateResponse(List<Candidate> candidates, UsageMetadata usageMetadata) {
        public record Candidate(Content content, String finishReason) {}
        public record UsageMetadata(int promptTokenCount, int candidatesTokenCount, int totalTokenCount){}
        public record Content(List<Part> parts) {}
        public record Part(String text) {}

        public String extractFirstText() {
            if (candidates == null || candidates.isEmpty()) return null;
            Candidate c = candidates.get(0);
            if (c == null || c.content() == null || c.content().parts() == null) return null;

            StringBuilder sb = new StringBuilder();
            for (Part p : c.content().parts()) {
                if (p == null || p.text() == null) continue;
                sb.append(p.text());
            }

            String result = sb.toString().trim();
            return result.isBlank() ? null : result;
        }

        public String firstFinishReasonRaw() {
            if (candidates == null || candidates.isEmpty()) return null;
            Candidate c = candidates.get(0);
            return (c == null) ? null : c.finishReason();
        }

        public LlmTokenUsage toTokenUsage() {
            if (usageMetadata == null) return LlmTokenUsage.empty();

            return new LlmTokenUsage(usageMetadata.promptTokenCount, usageMetadata.candidatesTokenCount);
        }

        public String safeDebug() {
            try {
                int size = (candidates == null) ? 0 : candidates.size();
                return "candidates.size=" + size;
            } catch (Exception e) {
                return "(debug unavailable)";
            }
        }
    }

    /**
     * RestClient에 타임아웃을 적용한 생성.
     * - connectTimeout / readTimeout
     * 추후에 한번더 확인해 구조 확인 필요
     */
    public static RestClient buildRestClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) connectTimeout.toMillis());
        f.setReadTimeout((int) readTimeout.toMillis());

        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(f)
            .build();
    }
    private static LlmFinishReason mapFinishReason(String raw) {
        if (raw == null || raw.isBlank()) return LlmFinishReason.UNKNOWN;

        return switch (raw) {
            case "STOP" -> LlmFinishReason.STOP;
            case "MAX_TOKENS" -> LlmFinishReason.MAX_TOKENS;
            case "SAFETY" -> LlmFinishReason.SAFETY;
            default -> LlmFinishReason.OTHER;
        };
    }

}
