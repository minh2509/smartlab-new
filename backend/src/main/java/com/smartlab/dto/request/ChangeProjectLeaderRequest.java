package com.smartlab.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Admin request for assigning a new primary project leader.")
public class ChangeProjectLeaderRequest {
    @NotBlank(message = "Primary leader user id is required")
    @Size(max = 36, message = "Primary leader user id must not exceed 36 characters")
    @Schema(description = "Public user id of the new primary leader", example = "d3151812-a9d9-4244-bd51-6c62d2f11c92")
    private String leaderUserId;
}
