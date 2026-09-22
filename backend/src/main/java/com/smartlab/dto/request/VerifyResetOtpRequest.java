package com.smartlab.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request body for checking a forgot-password OTP before setting a new password.")
public class VerifyResetOtpRequest {
    @Email(message = "Enter valid email address")
    @NotBlank(message = "Email is required")
    @Size(max = 254)
    @Schema(description = "Provisioned account email", example = "member@smartlab.local")
    private String email;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "[0-9]{6}", message = "OTP phải gồm đúng 6 chữ số")
    @Schema(description = "6-digit reset OTP sent to email", example = "123456")
    private String otp;
}
