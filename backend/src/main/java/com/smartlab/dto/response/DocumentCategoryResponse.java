package com.smartlab.dto.response;

import java.time.Instant;

public record DocumentCategoryResponse(
        Long id,
        String code,
        String name,
        String description,
        Integer displayOrder,
        Boolean isActive,
        long documentCount,
        Instant createdAt,
        Instant updatedAt
) { }
