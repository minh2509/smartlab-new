package com.smartlab.dto.request;

import com.smartlab.enums.AttachmentType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddAttachmentRequest {

    @NotNull
    private Long fileId;

    @NotNull
    private AttachmentType attachmentType;

    private String description;
}
