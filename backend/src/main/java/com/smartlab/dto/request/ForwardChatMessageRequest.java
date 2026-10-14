package com.smartlab.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForwardChatMessageRequest(
        @NotBlank @Size(max = 64) String clientMessageId
) {
}
