package com.smartlab.dto.response;

public record ChatReadResponse(
        String conversationId,
        String userId,
        long lastReadSeq
) {
}
