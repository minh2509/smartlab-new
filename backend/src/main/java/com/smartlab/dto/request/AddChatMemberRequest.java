package com.smartlab.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddChatMemberRequest(
        @NotBlank(message = "User id is required")
        @Size(max = 36, message = "User id must not exceed 36 characters")
        String userId
) {
}
