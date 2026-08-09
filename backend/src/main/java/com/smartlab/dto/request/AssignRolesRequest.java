package com.smartlab.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
@Schema(description = "Request body for replacing all roles of one user.")
public class AssignRolesRequest {
    @NotEmpty(message = "Role codes are required")
    @Schema(description = "Role codes to assign", example = "[\"LEADER\", \"MEMBER\"]")
    private Set<String> roleCodes;
}
