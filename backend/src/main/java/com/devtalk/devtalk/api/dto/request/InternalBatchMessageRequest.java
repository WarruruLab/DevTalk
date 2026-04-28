package com.devtalk.devtalk.api.dto.request;

import java.util.List;

public record InternalBatchMessageRequest(
    String sessionId,
    List<String> messageIds
) {}
