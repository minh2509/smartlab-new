package com.smartlab.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class DocumentResponse {
    private Long id;
    private Long projectId;
    private String title;
    private String description;
    private FileResponse currentFile;
    private Integer currentVersionNo;
    private DocumentUserResponse createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
