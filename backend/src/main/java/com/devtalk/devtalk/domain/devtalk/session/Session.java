package com.devtalk.devtalk.domain.devtalk.session;

import java.time.LocalDateTime;

public class Session {
    private String sessionId;
    private SessionStatus status;
    private final LocalDateTime createdAt;
    // 생성시에 createdAt 설정 및 id는 UUID 사용
    public Session(String sessionId){
        this.sessionId = sessionId;
        this.status = SessionStatus.OPEN;
        this.createdAt = LocalDateTime.now();
    }
    // 해결시에 status 변경
    public void resolve(){
        this.status = SessionStatus.RESOLVED;
    }
    // 필드에 대한 getter
    public String getSessionId() {
        return sessionId;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
