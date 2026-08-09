package com.smartlab.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Public request for accepting an account invitation.")
public class InvitationAcceptRequest {
    @NotBlank(message = "Token is required")
    @Schema(description = "Raw invitation token from the invite email link", example = "mUsOD7zti7x9u6oP3S8d")
    private String token;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password atleast 6 characters")
    @Schema(description = "Password to set for the account", example = "Member@123456")
    private String password;
}
