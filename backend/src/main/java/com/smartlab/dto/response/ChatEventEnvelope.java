package com.smartlab.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ChatEventEnvelope(
        String event,
        UUID eventId,
        Instant occurredAt,
        String conversationId,
        Object data
) {
}
