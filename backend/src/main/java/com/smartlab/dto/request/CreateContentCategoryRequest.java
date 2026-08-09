package com.smartlab.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateContentCategoryRequest {
    @NotBlank(message = "Code is required")
    @Size(max = 80)
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 150)
    private String name;

    @Size(max = 500)
    private String description;
}
