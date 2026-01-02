package com.devtalk.devtalk.controller.devtalk.message;

import com.devtalk.devtalk.controller.devtalk.InMemoryStore;
import com.devtalk.devtalk.domain.devtalk.message.Message;
import com.devtalk.devtalk.domain.devtalk.session.Session;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devtalk")
public class MessageController {

    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<Message> sendMessage(@PathVariable("sessionId")String sessionId, @RequestBody Message message){
        Session session = InMemoryStore.sessions.get(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        List<Message> messageList;
        if(InMemoryStore.messagesBySession.containsKey(sessionId)){
            messageList = InMemoryStore.messagesBySession.get(sessionId);
            messageList.add(message);
        }else{
            messageList = new ArrayList<>();
            messageList.add(message);
            InMemoryStore.messagesBySession.put(sessionId, messageList);
        }
        return ResponseEntity.ok(message);
    }
}
