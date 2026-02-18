package com.devtalk.devtalk.infra.persistence;

import com.devtalk.devtalk.domain.llm.LlmFinishReason;
import com.devtalk.devtalk.domain.message.Message;
import com.devtalk.devtalk.domain.message.MessageMarkers;
import com.devtalk.devtalk.domain.message.MessageMetadata;
import com.devtalk.devtalk.domain.message.MessageRepository;
import com.devtalk.devtalk.domain.message.MessageRole;
import com.devtalk.devtalk.domain.message.MessageStatus;
import com.devtalk.devtalk.domain.session.Session;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMessageRepository implements MessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMessageRepository(JdbcTemplate jdbcTemplate){
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Message save(Message message){
        String sql = "INSERT INTO message (message_id, session_id, role, content, status, markers, input_token_count, output_token_count, latency_ms, finish_reason, created_at) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String finishReason = (message.getMessageMetadata().finishReason() != null) ? message.getMessageMetadata().finishReason().name() : null;

        String markersStr = (message.getMarkers() != null) ? message.getMarkers().name() : null;

        jdbcTemplate.update(sql,
            message.getMessageId(),
            message.getSessionId(),
            message.getRole().name(),
            message.getContent(),
            message.getStatus().name(),
            markersStr,
            message.getMessageMetadata().inputTokenCount(),
            message.getMessageMetadata().outputTokenCount(),
            message.getMessageMetadata().latencyMs(),
            finishReason,
            message.getCreatedAt()
        );
        return message;
    }
    @Override
    public List<Message> findAllBySessionId(String sessionId){
        String sql = "SELECT * FROM message WHERE session_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, messageRowMapper, sessionId);
    }
    @Override
    public void deleteAllBySessionId(String sessionId){
        String sql = "DELETE FROM message WHERE session_id = ?";
        jdbcTemplate.update(sql, sessionId);
    }

    @Override
    public List<Message> findAllBySessionAfterCursor(String sessionId, LocalDateTime cursor, int limit){
        String sql = "SELECT * FROM message WHERE session_id = ? AND created_at > ? ORDER BY created_at ASC LIMIT ?";
        return jdbcTemplate.query(sql,messageRowMapper, sessionId, cursor, limit);
    }

    private final RowMapper<Message> messageRowMapper = (rs, rowNum) -> {
        MessageMetadata metadata = new MessageMetadata(
            rs.getInt("input_token_count"),
            rs.getInt("output_token_count"),
            rs.getLong("latency_ms"),
            resolveFinishReason(rs.getString("finish_reason"))
        );

        MessageMarkers markers = resolveMarkers(rs.getString("markers"));

        return new Message(
            rs.getString("message_id"),
            rs.getString("session_id"),
            MessageRole.valueOf(rs.getString("role")),
            rs.getString("content"),
            markers,
            MessageStatus.valueOf(rs.getString("status")),
            metadata,
            rs.getTimestamp("created_at").toLocalDateTime()
        );
    };

    private MessageMarkers resolveMarkers(String markersStr) {
        if (markersStr == null || markersStr.isBlank() || markersStr.equals("[]")) {
            return null;
        }
        try {
            return MessageMarkers.valueOf(markersStr);
        } catch (IllegalArgumentException e) {
            return null; // DB에 이상한 값이 있으면 그냥 null 처리
        }
    }

    private LlmFinishReason resolveFinishReason(String reason) {
        if (reason == null || reason.isBlank()) return LlmFinishReason.UNKNOWN;
        try {
            return LlmFinishReason.valueOf(reason);
        } catch (IllegalArgumentException e) {
            return LlmFinishReason.UNKNOWN;
        }
    }
}
