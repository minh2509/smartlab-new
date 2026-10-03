package com.smartlab.dto.response;

public record ChatMemberEventData(
        String conversationId,
        ChatMemberResponse member
) {
}
