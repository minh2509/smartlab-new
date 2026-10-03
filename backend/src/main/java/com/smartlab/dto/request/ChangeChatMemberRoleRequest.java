package com.smartlab.dto.request;

import com.smartlab.enums.ChatMembershipRole;
import jakarta.validation.constraints.NotNull;

public record ChangeChatMemberRoleRequest(
        @NotNull(message = "Membership role is required")
        ChatMembershipRole role
) {
}
