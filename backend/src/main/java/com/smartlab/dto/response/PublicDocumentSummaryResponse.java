package com.smartlab.dto.response;

import java.time.Instant;

public record PublicDocumentSummaryResponse(
        Long id,
        String title,
        String description,
        Long projectId,
        String projectCode,
        String projectName,
        Long currentFileId,
        String originalFileName,
        String mimeType,
        Long sizeBytes,
        Integer currentVersionNo,
        Instant updatedAt
) { }
