package com.smartlab.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request body for creating or updating a role.")
public class RoleRequest {
    @NotBlank(message = "Code is required")
    @Schema(description = "Stable role code", example = "RESEARCH_ASSISTANT")
    private String code;

    @NotBlank(message = "Name is required")
    @Schema(description = "Human-readable role name", example = "Research Assistant")
    private String name;

    @Schema(description = "Role purpose", example = "Custom role for members supporting research projects")
    private String description;

    @Schema(description = "If false, users owning this role cannot log in", example = "true")
    private Boolean isActive;
}
