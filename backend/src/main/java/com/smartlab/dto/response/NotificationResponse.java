package com.smartlab.dto.response;

import java.time.Instant;

public record NotificationResponse(
        Long id, Long actorUserId, String actorName, String type, String message,
        String relatedType, Long relatedId, String targetUrl,
        boolean isRead, Instant createdAt
) {
}
