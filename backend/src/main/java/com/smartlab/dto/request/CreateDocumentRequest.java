package com.smartlab.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class CreateDocumentRequest {
    @NotNull(message = "File is required")
    private MultipartFile file;

    @NotBlank(message = "Document title is required")
    @Size(max = 255, message = "Document title must not exceed 255 characters")
    private String title;

    @Size(max = 20_000, message = "Document description must not exceed 20000 characters")
    private String description;

    @Pattern(
            regexp = "(?i)PUBLIC|LAB|PROJECT|PRIVATE",
            message = "Access scope must be one of PUBLIC, LAB, PROJECT, or PRIVATE"
    )
    private String accessScope;

    @Size(max = 5_000, message = "Version note must not exceed 5000 characters")
    private String note;
}
