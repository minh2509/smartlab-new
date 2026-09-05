package com.smartlab.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateResearchFieldRequest {
    @NotBlank
    @Size(max = 80)
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,79}$",
            message = "code must start with a letter and contain only letters, numbers, or underscores")
    private String code;

    @NotBlank
    @Size(max = 150)
    private String name;

    @Size(max = 5000)
    private String description;

    private Long coverFileId;
}
