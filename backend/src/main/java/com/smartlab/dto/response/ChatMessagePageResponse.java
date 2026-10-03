package com.smartlab.dto.response;

import java.util.List;

public record ChatMessagePageResponse(
        List<ChatMessageResponse> messages,
        Long nextBeforeSeq,
        Long nextAfterSeq,
        boolean hasMore
) {
}
