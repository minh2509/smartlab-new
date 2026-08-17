package com.smartlab.service;

import com.smartlab.dto.response.TaskAttachmentResponse;
import com.smartlab.dto.response.TaskDetailResponse;
import com.smartlab.enums.AttachmentType;
import org.springframework.web.multipart.MultipartFile;

public interface TaskFileWorkflowService {

    TaskAttachmentResponse uploadAndAddAttachment(
            Long taskId,
            MultipartFile file,
            AttachmentType attachmentType,
            String description,
            String currentEmail
    );

    TaskDetailResponse uploadAndSubmit(
            Long taskId,
            MultipartFile file,
            String note,
            String currentEmail
    );
}
