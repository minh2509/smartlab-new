package com.smartlab.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

@Data
public class UpdateMemberProfileRequest {
    @Pattern(regexp = "^(|0[35789]\\d{8}|\\+84[35789]\\d{8})$", message = "Phone must be a valid Vietnamese phone number")
    @Size(max = 40)
    private String phone;

    @Email
    @Size(max = 190)
    private String publicEmail;

    @Size(max = 10000)
    private String bio;

    private LocalDate joinedLabAt;
    private Boolean clearJoinedLabAt;

    private Long avatarFileId;
    private Boolean removeAvatar;

    private Set<Long> researchFieldIds;
}
