package com.smartlab.dto.response;

import com.smartlab.enums.GalleryCategory;
import com.smartlab.enums.GalleryItemStatus;
import java.time.Instant;

public record AdminGalleryItemResponse(
        Long id,
        String title,
        String caption,
        String altText,
        GalleryCategory category,
        Long projectId,
        String projectName,
        Long eventId,
        String eventTitle,
        Long fileId,
        String originalFileName,
        String mimeType,
        Long sizeBytes,
        Instant capturedAt,
        GalleryItemStatus status,
        Boolean isFeatured,
        Instant publishedAt,
        Instant updatedAt
) {}
