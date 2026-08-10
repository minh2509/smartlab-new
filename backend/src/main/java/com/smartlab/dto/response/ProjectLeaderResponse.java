package com.smartlab.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "An equal project leader.")
public class ProjectLeaderResponse {
    @Schema(description = "Public user id", example = "d3151812-a9d9-4244-bd51-6c62d2f11c92")
    private String userId;

    @Schema(description = "Display name", example = "Nguyen Van A")
    private String name;
}
