package com.smartlab.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
@Schema(description = "Request body for replacing all permissions assigned to one role.")
public class RolePermissionRequest {
    @NotNull(message = "Permission codes are required")
    @Schema(description = "Full permission code list for the role", example = "[\"PROFILE_READ\", \"PROJECT_READ\", \"TASK_READ\"]")
    private Set<String> permissionCodes;
}
