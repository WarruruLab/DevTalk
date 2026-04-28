package com.devtalk.devtalk.api.controller.devtalk.internal;

import com.devtalk.devtalk.api.dto.request.InternalBatchMessageRequest;
import com.devtalk.devtalk.api.dto.response.InternalMessagePageResponse;
import com.devtalk.devtalk.api.dto.response.InternalMessageResponse;
import com.devtalk.devtalk.service.message.MessageService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal")
public class InternalController {
    private final MessageService messageService;

    public InternalController(MessageService messageService){
        this.messageService = messageService;
    }

    @GetMapping("/messages/{sessionId}")
    public ResponseEntity<InternalMessagePageResponse> getMessages(@PathVariable String sessionId, @RequestParam(name = "cursor", required = false) String cursor, @RequestParam(name = "limit", defaultValue = "100") String limit){
        return ResponseEntity.ok(messageService.getAllMessageByCursor(sessionId, cursor, limit));
    }

    @PostMapping("/messages/batch")
    public ResponseEntity<List<InternalMessageResponse>> getMessagesBatch(@RequestBody InternalBatchMessageRequest request){
        return ResponseEntity.ok(messageService.getMessagesByIds(request));
    }
}
