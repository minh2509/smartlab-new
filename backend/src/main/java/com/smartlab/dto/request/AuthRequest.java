package com.smartlab.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Login credentials for a provisioned SmartLab account.")
public class AuthRequest {
    @Schema(description = "Account email", example = "admin@smartlab.local")
    private String email;

    @Schema(description = "Account password", example = "Admin@123456")
    private String password;
}
