package com.smartlab.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostCommentRequest(
        @NotBlank(message = "Comment content is required")
        @Size(max = 5000, message = "Comment content must not exceed 5000 characters")
        String content
) {
}
