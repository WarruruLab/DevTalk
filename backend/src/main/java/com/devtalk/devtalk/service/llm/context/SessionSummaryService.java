package com.devtalk.devtalk.service.llm.context;

import com.devtalk.devtalk.config.LlmOptionsFactory;
import com.devtalk.devtalk.domain.llm.context.SessionSummaryStore;
import com.devtalk.devtalk.domain.llm.context.SummaryPolicy;
import com.devtalk.devtalk.domain.llm.context.SummaryState;
import com.devtalk.devtalk.domain.message.Message;
import com.devtalk.devtalk.domain.message.MessageMetadata;
import com.devtalk.devtalk.domain.message.MessageRepository;
import com.devtalk.devtalk.domain.message.MessageRole;
import com.devtalk.devtalk.domain.message.MessageStatus;
import com.devtalk.devtalk.domain.llm.LlmClient;
import com.devtalk.devtalk.domain.llm.LlmMessage;
import com.devtalk.devtalk.domain.llm.LlmRequest;
import com.devtalk.devtalk.domain.llm.LlmResult;
import com.devtalk.devtalk.domain.llm.LlmRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class SessionSummaryService {

    private final MessageRepository messageRepository;
    private final LlmClient llmClient;
    private final SessionSummaryStore summaryStore;
    private final SummaryPolicy policy;
    private final LlmOptionsFactory llmOptionsFactory;

    public SessionSummaryService(MessageRepository messageRepository, LlmClient llmClient, SessionSummaryStore summaryStore, SummaryPolicy policy, LlmOptionsFactory llmOptionsFactory) {
        this.messageRepository = Objects.requireNonNull(messageRepository);
        this.llmClient = Objects.requireNonNull(llmClient);
        this.summaryStore = Objects.requireNonNull(summaryStore);
        this.policy = Objects.requireNonNull(policy);
        this.llmOptionsFactory = Objects.requireNonNull(llmOptionsFactory);
    }

    /**
     * 요약 갱신 규칙:
     * - 요약 진행 지점(lastSummarizedIndex) 이후의 메시지 중
     *   최신 tail(policy.keepTailMessages)은 제외하고 요약에 반영
     * - 프롬프트: 1000자 이내 유도
     * - 서버: 1200자 하드 상한 (초과 시 재압축 1회)
     * - 실패는 SYSTEM FAILED 메시지로 기록하고, 요약 상태는 유지
     */
    public void updateIfNeeded(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");

        List<Message> history = messageRepository.findAllBySessionId(sessionId);
        if (history.isEmpty()) return;

        SummaryState state = summaryStore.getState(sessionId);

        int keepTail = Math.max(0, policy.keepTailMessages());
        int targetEndExclusive = Math.max(0, history.size() - keepTail);

        int start = Math.max(0, state.lastSummarizedIndex());
        if (targetEndExclusive <= start) return; // 새로 반영할 구간 없음

        List<Message> delta = history.subList(start, targetEndExclusive);

        LlmRequest req = buildUpdateRequest(state.summaryText(), delta, policy.promptMaxChars());
        LlmResult res = llmClient.generate(req);

        switch (res) {
            case LlmResult.Success s -> {
                String newSummary = normalize(s.text());

                if (newSummary.length() > policy.hardMaxChars()) {
                    String compressed = compressOnce(newSummary, policy.hardMaxChars());
                    if (compressed == null) {
                        appendSystemFailed(sessionId, "요약 재압축에 실패했습니다.");
                        return;
                    }
                    newSummary = compressed;
                }

                summaryStore.putState(sessionId, new SummaryState(newSummary, targetEndExclusive));
            }
            case LlmResult.Failure f -> {
                appendSystemFailed(sessionId, "요약 생성에 실패했습니다. (code=" + f.code() + ")");
            }
        }
    }

    private LlmRequest buildUpdateRequest(String existingSummary, List<Message> delta, int promptMaxChars) {
        String systemPrompt = """
            너는 DevTalk 세션 요약 생성기다.
            목적: 이후 대화에서 맥락을 안정적으로 복원할 수 있는 "세션 상태 요약"을 만든다.

            규칙:
            - Resolved를 판단하거나 변경하지 마라.
            - 사실과 추론을 구분하라.
            - 불확실한 내용은 단정하지 말고 '불확실'로 표기하라.
            - 요약은 %d자 이내로 작성하라.

            출력 형식(한국어):
            - 문제:
            - 시도:
            - 실패/원인:
            - 현재 결론/결정:
            - 남은 의문/다음 액션:
            """.formatted(promptMaxChars);

        List<LlmMessage> msgs = new ArrayList<>(delta.size() + 4);

        if (existingSummary != null && !existingSummary.isBlank()) {
            msgs.add(new LlmMessage(LlmRole.USER, "[기존 요약]\n" + existingSummary));
        }

        msgs.add(new LlmMessage(LlmRole.USER, "[새로 추가된 대화 구간]"));

        for (Message m : delta) {
            // 요약 대상 정책: SYSTEM 제외, FAILED 제외
            if (m.getRole() == MessageRole.SYSTEM) continue;
            if (m.getStatus() != MessageStatus.SUCCESS) continue;

            LlmRole role = (m.getRole() == MessageRole.USER) ? LlmRole.USER : LlmRole.AI;
            msgs.add(new LlmMessage(role, safe(m.getContent())));
        }

        return new LlmRequest(systemPrompt, List.copyOf(msgs), llmOptionsFactory.defaults());
    }

    private String compressOnce(String over, int hardMaxChars) {
        String systemPrompt = """
            너는 DevTalk 요약 압축기다.
            규칙:
            - 아래 요약을 더 짧게 압축하라.
            - 사실/결정/시도/실패/현재상태만 남겨라.
            - 불확실한 내용은 단정하지 말고 '불확실'로 표기하라.
            - %d자 이내로 작성하라.
            """.formatted(hardMaxChars);

        LlmRequest req = new LlmRequest(
            systemPrompt,
            List.of(new LlmMessage(LlmRole.USER, over)),
            llmOptionsFactory.defaults()
        );

        LlmResult res = llmClient.generate(req);

        return switch (res) {
            case LlmResult.Success s -> {
                String out = normalize(s.text());
                // 안전망: 그래도 초과하면(거의 없음) 마지막만 자름
                if (out.length() > hardMaxChars) yield out.substring(0, hardMaxChars);
                yield out;
            }
            case LlmResult.Failure f -> null;
        };
    }

    private void appendSystemFailed(String sessionId, String content) {
        messageRepository.save(new Message(
            sessionId,
            MessageRole.SYSTEM,
            content,
            null,
            MessageStatus.FAILED,
            MessageMetadata.empty()
        ));
    }

    private String safe(String s) {
        return (s == null) ? "" : s;
    }

    private String normalize(String s) {
        return (s == null) ? "" : s.trim();
    }
}
