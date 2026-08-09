package com.smartlab.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request body for creating or updating a backend permission.")
public class PermissionRequest {
    @NotBlank(message = "Code is required")
    @Schema(description = "Stable permission code used by @PreAuthorize checks", example = "PROJECT_MANAGE")
    private String code;

    @NotBlank(message = "Name is required")
    @Schema(description = "Human-readable permission name", example = "Manage projects")
    private String name;

    @Schema(description = "Feature/module grouping", example = "PROJECT")
    private String module;

    @Schema(description = "What this permission allows", example = "Create and update project data")
    private String description;

    @Schema(description = "Inactive permissions are ignored when calculating effective permissions", example = "true")
    private Boolean isActive;
}
