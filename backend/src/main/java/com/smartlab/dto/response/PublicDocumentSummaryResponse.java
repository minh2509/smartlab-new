package com.smartlab.dto.response;

import java.time.Instant;

public record PublicDocumentSummaryResponse(
        Long id,
        String title,
        String description,
        Long projectId,
        String projectCode,
        String projectName,
        Long categoryId,
        String categoryCode,
        String categoryName,
        Long currentFileId,
        String originalFileName,
        String mimeType,
        Long sizeBytes,
        Integer currentVersionNo,
        Instant archiveDate,
        Instant updatedAt
) {
    public PublicDocumentSummaryResponse(
            Long id, String title, String description, Long projectId, String projectCode, String projectName,
            Long categoryId, String categoryCode, String categoryName, Long currentFileId, String originalFileName,
            String mimeType, Long sizeBytes, Integer currentVersionNo, Instant updatedAt
    ) {
        this(id, title, description, projectId, projectCode, projectName, categoryId, categoryCode, categoryName,
                currentFileId, originalFileName, mimeType, sizeBytes, currentVersionNo, updatedAt, updatedAt);
    }
}
