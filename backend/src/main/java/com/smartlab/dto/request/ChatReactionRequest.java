package com.smartlab.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatReactionRequest(
        @NotBlank @Size(max = 16) String emoji
) {
}
