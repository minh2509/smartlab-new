package com.smartlab.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Standard error response returned by authentication endpoints.")
public class ErrorResponse {
    @Schema(description = "Whether the response represents an error.", example = "true")
    private Boolean error;

    @Schema(description = "Human-readable error message.", example = "Authentication Failed")
    private String message;
}
