package com.smartlab.dto.response;

public record ChatTypingEventData(
        String conversationId,
        ChatUserSummary user
) {
}
