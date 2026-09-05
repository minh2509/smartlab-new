package com.smartlab.dto.response;

import com.smartlab.enums.LabArticleStatus;

import java.time.Instant;
import java.util.Map;

public record AdminLabArticleResponse(Long id, String title, String slug, String excerpt, Map<String, Object> content,
        LabArticleStatus status, Instant publishedAt, Instant createdAt, Instant updatedAt) { }
