package com.smartlab.dto.response;

import com.smartlab.enums.ChatMessageType;

public record ChatMessageReferenceResponse(
        Long id,
        ChatUserSummary sender,
        ChatMessageType messageType,
        String content,
        boolean deleted
) {
}
