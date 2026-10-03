package com.smartlab.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateGroupChatRequest(
        @NotBlank(message = "Group title is required")
        @Size(max = 200, message = "Group title must not exceed 200 characters")
        String title,
        @Valid
        @NotNull(message = "Member user ids are required")
        @Size(max = 100, message = "A group cannot have more than 100 requested members")
        List<@NotBlank @Size(max = 36) String> memberUserIds,
        @Positive(message = "Project id must be positive")
        Long projectId
) {
}
