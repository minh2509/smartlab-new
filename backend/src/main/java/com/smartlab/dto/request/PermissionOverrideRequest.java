package com.smartlab.dto.request;

import com.smartlab.enums.PermissionOverrideEffect;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request body for granting or denying one permission directly to a user.")
public class PermissionOverrideRequest {
    @NotNull(message = "Effect is required")
    @Schema(description = "GRANT adds the permission, DENY removes it from effective permissions.", example = "GRANT")
    private PermissionOverrideEffect effect;
}
