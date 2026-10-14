package com.smartlab.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatPollVoteRequest(
        @NotBlank @Size(max = 80) String optionId
) {
}
