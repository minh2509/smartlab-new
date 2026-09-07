package com.smartlab.dto.response;

import java.time.Instant;

public record PublicLabArticleSummaryResponse(Long id, String title, String slug, String excerpt, Instant publishedAt) { }
