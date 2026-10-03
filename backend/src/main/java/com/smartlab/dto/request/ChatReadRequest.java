package com.smartlab.dto.request;

import jakarta.validation.constraints.PositiveOrZero;

public record ChatReadRequest(
        @PositiveOrZero(message = "Last read sequence must not be negative")
        long lastReadSeq
) {
}
