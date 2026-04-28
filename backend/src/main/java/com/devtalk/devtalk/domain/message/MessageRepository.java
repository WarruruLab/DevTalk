package com.devtalk.devtalk.domain.message;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageRepository {
    Message save(Message message);
    List<Message> findAllBySessionId(String sessionId);
    void deleteAllBySessionId(String sessionId);
    List<Message> findAllBySessionAfterCursor(String sessionId, LocalDateTime cursor, int limit);
    List<Message> findAllBySessionAndIds(String sessionId, List<String> messageIds);
}
