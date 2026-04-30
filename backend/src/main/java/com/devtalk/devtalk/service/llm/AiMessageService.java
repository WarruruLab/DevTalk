package com.devtalk.devtalk.service.llm;

import com.devtalk.devtalk.api.dto.response.MessageResponse;
import com.devtalk.devtalk.config.LlmOptionsFactory;
import com.devtalk.devtalk.config.LlmProperties;
import com.devtalk.devtalk.domain.message.Message;
import com.devtalk.devtalk.domain.message.MessageMarkers;
import com.devtalk.devtalk.domain.message.MessageMetadata;
import com.devtalk.devtalk.domain.message.MessageRepository;
import com.devtalk.devtalk.domain.message.MessageRole;
import com.devtalk.devtalk.domain.message.MessageStatus;
import com.devtalk.devtalk.domain.llm.LlmClient;
import com.devtalk.devtalk.domain.llm.LlmFinishReason;
import com.devtalk.devtalk.domain.llm.LlmMessage;
import com.devtalk.devtalk.domain.llm.LlmOptions;
import com.devtalk.devtalk.service.llm.context.LlmPromptComposer;
import com.devtalk.devtalk.domain.llm.LlmRequest;
import com.devtalk.devtalk.domain.llm.LlmResult;
import com.devtalk.devtalk.domain.llm.LlmRole;
import com.devtalk.devtalk.service.llm.context.SessionSummaryService;
import com.devtalk.devtalk.domain.llm.context.SessionSummaryStore;
import com.devtalk.devtalk.domain.llm.context.TailSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public final class AiMessageService {

    private final MessageRepository messageRepository;
    private final LlmClient llmClient;

    private final TailSelector tailSelector;
    private final SessionSummaryStore sessionSummaryStore;
    private final SessionSummaryService sessionSummaryService;
    private final LlmPromptComposer promptComposer;
    private final LlmOptionsFactory llmOptionsFactory;
    private final int maxContinueRounds;

    private static final String CONTINUE_PROMPT = """
        출력 길이 제한으로 이전 답변이 중간에 끊겼습니다.
        
        아래 규칙을 반드시 지켜서 이어서 작성하세요.
        
        [규칙]
        1. 이미 출력된 문장이나 표현을 **단 한 글자도 반복하지 마세요**.
        2. 반드시 **직전 문장의 마지막 문자 바로 다음 내용부터** 이어서 작성하세요.
        3. "..." , "…", "(계속)", "이어서", "다음과 같습니다" 등의 표현을 **절대 사용하지 마세요**.
        4. 새 서론, 요약, 인사말을 추가하지 마세요.
        5. 오직 이어서 작성만 하세요.
        
        위 규칙을 어기면 응답은 잘못된 것으로 간주됩니다.
    """;

    public AiMessageService(MessageRepository messageRepository, LlmClient llmClient, TailSelector tailSelector, SessionSummaryService sessionSummaryService, SessionSummaryStore sessionSummaryStore, LlmPromptComposer promptComposer, LlmOptionsFactory llmOptionsFactory, LlmProperties llmProperties) {
        this.messageRepository = Objects.requireNonNull(messageRepository);
        this.llmClient = Objects.requireNonNull(llmClient);
        this.tailSelector = tailSelector;
        this.sessionSummaryService = sessionSummaryService;
        this.sessionSummaryStore = sessionSummaryStore;
        this.promptComposer = promptComposer;
        this.llmOptionsFactory = Objects.requireNonNull(llmOptionsFactory);
        this.maxContinueRounds = llmProperties.resolvedContext().resolvedContinue().maxRounds();
    }

    public MessageResponse generateAndSave(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");

        // 1) 전체 히스토리 조회
        List<Message> history = messageRepository.findAllBySessionId(sessionId);

        // 2) 최신 SUCCESS USER 추출 (없으면 실패 기록)
        Optional<Message> latestUserOpt = findLatestSuccessUser(history);
        if (latestUserOpt.isEmpty()) {
            Message failed = new Message(
                sessionId,
                MessageRole.AI,
                "AI 응답 생성에 실패했습니다. 최신 USER 메시지를 찾을 수 없습니다.",
                null,
                MessageStatus.FAILED,
                MessageMetadata.empty()
            );
            return MessageResponse.from(messageRepository.save(failed));
        }
        Message latestUser = latestUserOpt.get();

        // 3) 요약 갱신 (필요한 경우에만 내부에서 수행)
        //    - 실패해도 내부에서 SYSTEM FAILED 로깅하고 계속 진행(요약은 기존 상태 유지)
        sessionSummaryService.updateIfNeeded(sessionId);

        // 4) Tail 선택 (SYSTEM 제외, FAILED 제외, AI 포함, latestUser 제외)
        List<Message> tail = tailSelector.selectTail(history, latestUser);

        // 5) 요약 조회 (없으면 기본 문구)
        String summary = sessionSummaryStore.getState(sessionId).summaryText();

        // 6) Prompt 조립: SUMMARY(system_prompt) + messages(tail -> latestUser)
        LlmPromptComposer.ComposedPrompt composed =
            promptComposer.compose(summary, tail, latestUser);

        // 7) DevTalk 고정 systemPrompt + SUMMARY 결합
        String baseSystemPrompt = """
                너는 DevTalk의 AI 응답자다.
                - Resolved를 판단하거나 변경하지 마라
                - 사실과 추론을 구분해라
                - 모르면 모른다고 말해라
                """;

        String systemPrompt = baseSystemPrompt + "\n" + composed.systemPrompt();
        List<LlmMessage> baseContext = composed.messages();

        LlmOptions options = llmOptionsFactory.defaults();

        // 8) LLM 호출 (자동 이어쓰기 포함)
        StringBuilder total = new StringBuilder();
        LlmFinishReason lastReason = LlmFinishReason.UNKNOWN;
        int totalInputToken = 0;
        int totalOutputToken = 0;
        long startTime = System.currentTimeMillis();

        int maxRounds = 1 + maxContinueRounds;
        for (int round = 1; round <= maxRounds; round++) {
            List<LlmMessage> ctx = (round == 1)
                ? baseContext
                : buildContinueContext(baseContext, total.toString());

            LlmRequest request = new LlmRequest(systemPrompt, ctx, options);
            LlmResult result = llmClient.generate(request);

            if (result instanceof LlmResult.Failure f) {
                Message failed = new Message(
                    sessionId,
                    MessageRole.AI,
                    "AI 응답 생성에 실패했습니다. 잠시 후 다시 시도해주세요."
                        + "\n(code=" + f.code() + ")",
                    null,
                    MessageStatus.FAILED,
                    MessageMetadata.empty()
                );
                return MessageResponse.from(messageRepository.save(failed));
            }

            LlmResult.Success s = (LlmResult.Success) result;
            total.append(s.text());
            lastReason = s.finishReason();
            totalInputToken += s.tokenUsage().inputTokenCount();
            totalOutputToken += s.tokenUsage().outputTokenCount();

            // ✅ 토큰 때문에 끊긴 경우에만 이어쓰기
            if (lastReason != LlmFinishReason.MAX_TOKENS) {
                break;
            }
        }
        long endTime = System.currentTimeMillis();

        // 9) 최종 결과 저장 (유저 입장에서 1개의 자연스러운 답변)
        Message aiMessage = new Message(
            sessionId,
            MessageRole.AI,
            total.toString(),
            (MessageMarkers) null,
            MessageStatus.SUCCESS,
            new MessageMetadata(totalInputToken, totalOutputToken, endTime - startTime, lastReason)
        );

        return MessageResponse.from(messageRepository.save(aiMessage));
    }

    private List<LlmMessage> buildContinueContext(List<LlmMessage> baseContext, String assistantSoFar) {
        List<LlmMessage> merged = new ArrayList<>(baseContext.size() + 2);
        merged.addAll(baseContext);

        // “지금까지 AI가 말한 내용”을 넣고,
        merged.add(new LlmMessage(LlmRole.AI, assistantSoFar));

        // “이어서 계속”을 user로 넣는다
        merged.add(new LlmMessage(LlmRole.USER, CONTINUE_PROMPT));

        return merged;
    }

    private Optional<Message> findLatestSuccessUser(List<Message> historyInOrder) {
        for (int i = historyInOrder.size() - 1; i >= 0; i--) {
            Message m = historyInOrder.get(i);
            if (m.getRole() == MessageRole.USER && m.getStatus() == MessageStatus.SUCCESS) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

}
