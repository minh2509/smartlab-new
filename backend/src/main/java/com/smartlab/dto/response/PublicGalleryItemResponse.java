package com.smartlab.dto.response;

import com.smartlab.enums.GalleryCategory;
import java.time.Instant;

public record PublicGalleryItemResponse(
        Long id,
        String title,
        String caption,
        String altText,
        GalleryCategory category,
        Long projectId,
        String projectCode,
        String projectName,
        Long eventId,
        String eventTitle,
        Long fileId,
        String originalFileName,
        String mimeType,
        Long sizeBytes,
        Instant capturedAt,
        Instant publishedAt,
        Boolean isFeatured
) {}
