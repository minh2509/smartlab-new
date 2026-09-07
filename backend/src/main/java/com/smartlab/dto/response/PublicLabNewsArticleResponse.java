package com.smartlab.dto.response;

import java.time.Instant;

public record PublicLabNewsArticleResponse(Long id, String title, String excerpt, String sourceName, String sourceUrl,
        Instant publishedAt) { }
