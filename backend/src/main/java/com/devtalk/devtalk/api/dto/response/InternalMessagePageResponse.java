package com.devtalk.devtalk.api.dto.response;

import java.util.List;

public record InternalMessagePageResponse (
    List<InternalMessageResponse> messages, // 실제 메시지 데이터
    String nextCursor,                     // 다음 조회를 위해 갱신후 넘기기
    boolean hasMore                        // 데이터가 아직 남아있는지에 대한 여부
){}
