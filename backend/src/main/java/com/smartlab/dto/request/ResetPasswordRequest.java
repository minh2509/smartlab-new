package com.smartlab.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request body for forgot-password reset by OTP.")
public class ResetPasswordRequest {
    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 72, message = "Mật khẩu phải có từ 6 đến 72 ký tự")
    @Schema(description = "New account password", example = "NewPassword@123")
    private String newPassword;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "[0-9]{6}", message = "OTP phải gồm đúng 6 chữ số")
    @Schema(description = "6-digit reset OTP sent to email", example = "123456")
    private String otp;

    @NotBlank(message = "Email is required")
    @Email(message = "Email không hợp lệ")
    @Size(max = 254)
    @Schema(description = "Account email", example = "member@smartlab.local")
    private String email;

}
