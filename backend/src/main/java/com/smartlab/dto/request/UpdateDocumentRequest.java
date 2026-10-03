package com.smartlab.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateDocumentRequest {
    @NotBlank(message = "Document title is required")
    @Size(max = 255, message = "Document title must not exceed 255 characters")
    private String title;

    @Size(max = 20_000, message = "Document description must not exceed 20000 characters")
    private String description;
}
