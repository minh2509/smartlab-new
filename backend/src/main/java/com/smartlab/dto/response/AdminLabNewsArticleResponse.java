package com.smartlab.dto.response;

import java.time.Instant;

public record AdminLabNewsArticleResponse(Long id, String title, String excerpt, String sourceName, String sourceUrl,
        Instant publishedAt, Boolean isPublic, Instant createdAt, Instant updatedAt) { }
