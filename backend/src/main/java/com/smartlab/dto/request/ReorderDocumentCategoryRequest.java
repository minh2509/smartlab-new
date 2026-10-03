package com.smartlab.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ReorderDocumentCategoryRequest(
        @NotEmpty(message = "Category order items must not be empty")
        @Valid
        List<OrderItem> items
) {
    public record OrderItem(
            @NotNull(message = "Category ID is required")
            @NotNull(message = "Category ID is required")
            @Positive(message = "Category ID must be positive")
            Long id,
            @NotNull(message = "Display order is required")
            @Min(value = 0, message = "Display order must not be negative")
            Integer displayOrder
    ) { }
}
