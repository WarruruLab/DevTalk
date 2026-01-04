package com.devtalk.devtalk.domain.devtalk.message;

import java.time.LocalDateTime;

public class Message {
    private final String id;
    private final MessageRole role;
    private final String content;
    private final MessageStatus status;
    private final LocalDateTime createdAt;

    public Message(String id, MessageRole role, String content, MessageStatus status) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public MessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

}
