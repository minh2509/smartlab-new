package com.smartlab.dto.response;

import com.smartlab.enums.ChatConversationType;

import java.time.Instant;
import java.util.List;

public record ChatConversationResponse(
        String conversationId,
        ChatConversationType type,
        String displayName,
        String title,
        Long projectId,
        List<ChatMemberResponse> members,
        ChatMessageResponse lastMessage,
        long lastMessageSeq,
        long lastReadSeq,
        long unreadCount,
        Instant createdAt,
        Instant updatedAt
) {
}
