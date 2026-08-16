package com.smartlab.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class DocumentVersionResponse {
    private Long id;
    private Long documentId;
    private Integer versionNo;
    private FileResponse file;
    private DocumentUserResponse uploadedBy;
    private String note;
    private Instant createdAt;
}
