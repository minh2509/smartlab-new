package com.smartlab.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
@Schema(description = "Account view returned to admins and current-profile calls.")
public class AccountResponse {
    @Schema(description = "Public user id", example = "d3151812-a9d9-4244-bd51-6c62d2f11c92")
    private String userId;

    @Schema(description = "Display name", example = "Nguyen Van A")
    private String name;

    @Schema(description = "Login email", example = "member@smartlab.local")
    private String email;

    @Schema(description = "Whether this user is allowed to log in", example = "true")
    private Boolean isActive;

    @Schema(description = "Whether invitation/setup has been completed", example = "true")
    private Boolean isAccountVerified;

    @Schema(description = "Assigned role codes", example = "[\"MEMBER\"]")
    private Set<String> roles;

    @Schema(description = "Effective permissions after role permissions and user overrides", example = "[\"PROFILE_READ\", \"PROJECT_READ\"]")
    private Set<String> permissions;
}
