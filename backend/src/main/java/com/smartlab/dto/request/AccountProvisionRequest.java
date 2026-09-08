package com.smartlab.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

@Data
@Schema(description = "Admin request for provisioning an account. Public registration is disabled.")
public class AccountProvisionRequest {
    @NotBlank(message = "Name is required")
    @Schema(description = "Display name of the new member", example = "Nguyen Van A")
    private String name;

    @Email(message = "Enter valid email address")
    @NotBlank(message = "Email is required")
    @Schema(description = "Unique email for login and invitation", example = "member@smartlab.local")
    private String email;

    @Schema(description = "Initial active role codes. At least one role is required.", example = "[\"MEMBER\"]")
    private Set<String> roleCodes;
}
