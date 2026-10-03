package com.smartlab.dto.response;

import com.smartlab.enums.ChatMembershipRole;

import java.time.Instant;

public record ChatMemberResponse(
        String userId,
        String name,
        ChatMembershipRole role,
        Instant joinedAt,
        Instant leftAt,
        long lastReadSeq,
        boolean active
) {
}
