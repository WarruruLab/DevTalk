package com.devtalk.devtalk.service.message;

import com.devtalk.devtalk.api.dto.request.InternalBatchMessageRequest;
import com.devtalk.devtalk.api.dto.request.SendMessageRequest;
import com.devtalk.devtalk.api.dto.response.InternalMessagePageResponse;
import com.devtalk.devtalk.api.dto.response.InternalMessageResponse;
import com.devtalk.devtalk.api.dto.response.MessageResponse;
import com.devtalk.devtalk.domain.message.Message;
import com.devtalk.devtalk.domain.message.MessageRepository;
import com.devtalk.devtalk.domain.session.Session;
import com.devtalk.devtalk.domain.session.SessionRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final SessionRepository sessionRepository;

    public MessageService(MessageRepository messageRepository, SessionRepository sessionRepository){
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
    }

    public MessageResponse append(String sessionId, SendMessageRequest sendMessageRequest){
        verifySession(sessionId);
        Message message = sendMessageRequest.toDomain(sessionId);
        Session session = sessionRepository.findById(sessionId).get();
        session.updateLastUpdatedAt();
        return MessageResponse.from(messageRepository.save(message));
    }

    public List<MessageResponse> getAll(String sessionId){
        verifySession(sessionId);
        return messageRepository.findAllBySessionId(sessionId).stream()
            .map(MessageResponse::from)
            .toList();
    }

    public void deleteAll(String sessionId){
        verifySession(sessionId);
        messageRepository.deleteAllBySessionId(sessionId);
    }

    public InternalMessagePageResponse getAllMessageByCursor(String sessionId, String cursor, String limit) {
        int parsedLimit = parseLimit(limit);

        // LocalDateTime으로 변환
        LocalDateTime cursorTime = parseCursor(cursor);

        // message 가져오기
        List<Message> messages = messageRepository.findAllBySessionAfterCursor(sessionId, cursorTime, parsedLimit + 1);
        // 추가로 있는지에 대한 여부 확인
        boolean hasMore = messages.size() > parsedLimit;
        // limit까지만 가져오기
        List<Message> resultMessages = hasMore ? messages.subList(0, parsedLimit) : messages;
        // 다음 cursor 활성화
        String nextCursor = resultMessages.isEmpty() ? null : resultMessages.get(resultMessages.size() - 1).getCreatedAt().toString();

        List<InternalMessageResponse> responseList = resultMessages
            .stream()
            .map(InternalMessageResponse::from)
            .toList();

        return new InternalMessagePageResponse(responseList, nextCursor, hasMore);
    }

    public List<InternalMessageResponse> getMessagesByIds(InternalBatchMessageRequest request){
        if (request.messageIds() == null || request.messageIds().isEmpty()) {
            return Collections.emptyList();
        }

        return messageRepository.findAllBySessionAndIds(request.sessionId(), request.messageIds())
            .stream()
            .map(InternalMessageResponse::from)
            .toList();
    }

    private Session verifySession(String sessionId){
        return sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("session not found"));
    }

    private int parseLimit(String limit) {
        try {
            int val = (limit != null) ? Integer.parseInt(limit) : 100;
            return Math.min(val, 500); // 최대 개수 제한(보안/성능 목적)
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    private LocalDateTime parseCursor(String cursor) {
        try {
            // "2026-02-18T17:00:00" 형태의 문자열을 파싱합니다.
            return (cursor != null && !cursor.isEmpty()) ? LocalDateTime.parse(cursor) : LocalDateTime.of(1970, 1, 1, 0, 0);
        } catch (DateTimeParseException e) {
            // 파싱 에러 시 기본값 처리
            return LocalDateTime.of(1970, 1, 1, 0, 0);
        }
    }
}
