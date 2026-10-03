package com.smartlab.dto.response;

import com.smartlab.enums.ChatConversationType;

import java.time.Instant;
import java.util.List;

public record ChatConversationEventData(
        String conversationId,
        ChatConversationType type,
        String title,
        Long projectId,
        List<ChatMemberResponse> members,
        long lastMessageSeq,
        Instant updatedAt
) {
}
