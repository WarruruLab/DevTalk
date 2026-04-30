package com.devtalk.devtalk.api.controller.devtalk.llm;

import com.devtalk.devtalk.config.LlmProperties;
import com.devtalk.devtalk.service.llm.AiStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping(("/api/devtalk/sessions"))
public class LlmStreamController {
    private final AiStreamService aiStreamService;
    private final LlmProperties llmProperties;

    public LlmStreamController(AiStreamService aiStreamService, LlmProperties llmProperties) {
        this.aiStreamService = aiStreamService;
        this.llmProperties = llmProperties;
    }

    @GetMapping(value = "/{sessionId}/ai/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String sessionId, @RequestParam(required = false) String replyTo) {
        SseEmitter emitter = new SseEmitter(llmProperties.resolvedStream().sseEmitterTimeoutMs());
        aiStreamService.streamAi(sessionId, replyTo, emitter);
        return emitter;
    }
}
