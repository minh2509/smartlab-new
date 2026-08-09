package com.smartlab.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request body for forgot-password reset by OTP.")
public class ResetPasswordRequest {
    @NotBlank(message = "Password is required")
    @Schema(description = "New account password", example = "NewPassword@123")
    private String newPassword;

    @NotBlank(message = "OTP is required")
    @Schema(description = "6-digit reset OTP sent to email", example = "123456")
    private String otp;

    @NotBlank(message = "Email is required")
    @Schema(description = "Account email", example = "member@smartlab.local")
    private String email;

}
