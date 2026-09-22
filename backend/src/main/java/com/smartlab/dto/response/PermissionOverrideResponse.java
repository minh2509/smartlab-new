package com.smartlab.dto.response;

import com.smartlab.enums.PermissionOverrideEffect;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "One explicit permission grant or denial applied to a user.")
public class PermissionOverrideResponse {
    @Schema(description = "Permission code affected by this override", example = "PROJECT_MANAGE")
    private String permissionCode;

    @Schema(description = "Whether the permission is explicitly granted or denied", example = "DENY")
    private PermissionOverrideEffect effect;
}
