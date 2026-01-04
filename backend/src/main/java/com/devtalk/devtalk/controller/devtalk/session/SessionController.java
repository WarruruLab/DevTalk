package com.devtalk.devtalk.controller.devtalk.session;

import com.devtalk.devtalk.controller.devtalk.InMemoryStore;
import com.devtalk.devtalk.domain.devtalk.session.Session;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devtalk/sessions")
public class SessionController {

    @GetMapping("/{sessionId}")
    public ResponseEntity<Session> getSession(@PathVariable("sessionId") String sessionId){
        Session session = InMemoryStore.sessions.get(sessionId);
        return ResponseEntity.ok(session);
    }

    @PostMapping
    public ResponseEntity<Session> createSession(){
        String sessionId = UUID.randomUUID().toString();
        Session session = new Session(sessionId);
        InMemoryStore.sessions.put(sessionId, session);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/{sessionId}/resolve")
    public ResponseEntity<Session> resolveSession(@PathVariable("sessionId")String sessionId){
        Session session = InMemoryStore.sessions.get(sessionId);
        session.resolve();
        return ResponseEntity.ok(session);
    }

}
