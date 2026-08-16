package com.smartlab.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddProjectMemberRequest {
    @NotBlank(message = "Member user id is required")
    @Size(max = 36, message = "Member user id must not exceed 36 characters")
    private String userId;
}
