package com.devtalk.devtalk.controller.devtalk;

import com.devtalk.devtalk.domain.devtalk.message.Message;
import com.devtalk.devtalk.domain.devtalk.session.Session;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryStore {

    /** 세션 저장소 (sessionId -> Session) */
    public static final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /** 메시지 저장소 (sessionId -> messages) */
    public static final Map<String, List<Message>> messagesBySession = new ConcurrentHashMap<>();

    private InMemoryStore() {
    }
}
