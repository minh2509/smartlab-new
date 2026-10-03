package com.smartlab.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDocumentCategoryRequest(
        @NotBlank(message = "Category code is required")
        @Size(max = 80, message = "Category code must not exceed 80 characters")
        String code,

        @NotBlank(message = "Category name is required")
        @Size(max = 150, message = "Category name must not exceed 150 characters")
        String name,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        Integer displayOrder,

        Boolean isActive
) { }
