package com.smartlab.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record AssignDocumentCategoryRequest(
        @NotEmpty(message = "Document IDs must not be empty")
        List<@NotNull @Positive Long> documentIds,

        Long categoryId
) { }
