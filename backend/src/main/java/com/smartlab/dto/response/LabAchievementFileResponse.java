package com.smartlab.dto.response;

import java.time.Instant;

public record LabAchievementFileResponse(Long id, Long fileId, String originalName, String mimeType, Long sizeBytes,
                                         String label, Integer sortOrder, Instant createdAt) { }
