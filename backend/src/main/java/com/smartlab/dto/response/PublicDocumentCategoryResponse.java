package com.smartlab.dto.response;

public record PublicDocumentCategoryResponse(
        Long id,
        String code,
        String name,
        String description,
        Integer displayOrder,
        long documentCount
) { }
