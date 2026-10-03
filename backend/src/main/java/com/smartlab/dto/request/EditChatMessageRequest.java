package com.smartlab.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditChatMessageRequest(
        @NotBlank(message = "Message content is required")
        @Size(max = 20_000, message = "Message content must not exceed 20000 characters")
        String content
) {
}
