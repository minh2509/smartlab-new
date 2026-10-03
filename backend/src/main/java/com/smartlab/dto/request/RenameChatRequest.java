package com.smartlab.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameChatRequest(
        @NotBlank(message = "Group title is required")
        @Size(max = 200, message = "Group title must not exceed 200 characters")
        String title
) {
}
