package com.smartlab.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatSuccessorRequest(
        @NotBlank @Size(max = 36) String successorUserId
) {
}
