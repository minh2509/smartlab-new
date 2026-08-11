package com.smartlab.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class TaskAttachmentResponse {
    private Long id;
    private Long fileId;
    private String originalName;
    private String mimeType;
    private Long sizeBytes;
    private String attachmentType;
    private String description;
    private Long uploadedByUserId;
    private String uploadedByName;
    private Instant createdAt;
}
