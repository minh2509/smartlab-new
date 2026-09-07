package com.smartlab.dto.response;

import java.time.Instant;

public record LabNewsArticleResponse(Long id, String title, String excerpt, String sourceName, String sourceUrl,
        Instant publishedAt, Boolean isPublic, Instant createdAt, Instant updatedAt, Instant deletedAt) { }
