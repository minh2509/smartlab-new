package com.smartlab.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class FileResponse {
    private Long id;
    private String originalName;
    private String mimeType;
    private Long sizeBytes;
    private String accessScope;
    private String description;
    private Instant createdAt;
}
