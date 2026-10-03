package com.smartlab.dto.response;

public record ChatMessageFileResponse(
        Long fileId,
        String originalName,
        String mimeType,
        Long sizeBytes,
        String accessScope
) {
}
