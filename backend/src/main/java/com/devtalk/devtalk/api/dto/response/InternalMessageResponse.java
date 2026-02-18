package com.devtalk.devtalk.api.dto.response;

import com.devtalk.devtalk.domain.message.Message;
import com.devtalk.devtalk.domain.message.MessageRole;
import java.time.LocalDateTime;

public record InternalMessageResponse(
    String id,
    String content,
    MessageRole role,
    LocalDateTime createdAt
) {
    public static InternalMessageResponse from(Message message) {
        return new InternalMessageResponse(
            message.getMessageId(),
            message.getContent(),
            message.getRole(),
            message.getCreatedAt()
        );
    }
}
