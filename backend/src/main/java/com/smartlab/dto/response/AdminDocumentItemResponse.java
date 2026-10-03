package com.smartlab.dto.response;

import java.time.Instant;

public record AdminDocumentItemResponse(
        Long id,
        String title,
        Long projectId,
        String projectCode,
        String projectName,
        Long categoryId,
        String categoryName,
        String fileName,
        String accessScope,
        Instant updatedAt
) { }
