package com.smartlab.dto.response;

import com.smartlab.enums.ChatMessageType;

import java.time.Instant;
import java.util.List;

public record ChatMessageResponse(
        Long id,
        String conversationId,
        long messageSeq,
        ChatUserSummary sender,
        String clientMessageId,
        ChatMessageType messageType,
        String content,
        Long replyToMessageId,
        List<ChatMessageFileResponse> files,
        Instant createdAt,
        Instant editedAt,
        Instant deletedAt
) {
}
