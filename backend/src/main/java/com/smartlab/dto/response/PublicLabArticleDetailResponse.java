package com.smartlab.dto.response;

import java.time.Instant;
import java.util.Map;

public record PublicLabArticleDetailResponse(Long id, String title, String slug, String excerpt,
        Map<String, Object> content, Instant publishedAt) { }
