package com.smartlab.service.impl;

import com.smartlab.dto.request.AddAttachmentRequest;
import com.smartlab.dto.request.SubmitTaskRequest;
import com.smartlab.dto.response.FileResponse;
import com.smartlab.dto.response.TaskAttachmentResponse;
import com.smartlab.dto.response.TaskDetailResponse;
import com.smartlab.enums.AttachmentType;
import com.smartlab.service.FileService;
import com.smartlab.service.TaskFileWorkflowService;
import com.smartlab.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class TaskFileWorkflowServiceImpl implements TaskFileWorkflowService {

    private final FileService fileService;
    private final TaskService taskService;

    @Override
    @Transactional
    public TaskAttachmentResponse uploadAndAddAttachment(
            Long taskId,
            MultipartFile file,
            AttachmentType attachmentType,
            String description,
            String currentEmail
    ) {
        Long projectId = taskService.requireAttachmentUploadProjectId(taskId, currentEmail);
        FileResponse uploaded = fileService.uploadForTaskProject(file, description, currentEmail, projectId);
        AddAttachmentRequest request = new AddAttachmentRequest();
        request.setFileId(uploaded.getId());
        request.setAttachmentType(attachmentType);
        request.setDescription(description);
        return taskService.addAttachment(taskId, request, currentEmail);
    }

    @Override
    @Transactional
    public TaskDetailResponse uploadAndSubmit(
            Long taskId,
            MultipartFile file,
            String note,
            String currentEmail
    ) {
        Long projectId = taskService.requireSubmissionUploadProjectId(taskId, currentEmail);
        FileResponse uploaded = fileService.uploadForTaskProject(
                file, "Task Submission Result", currentEmail, projectId);
        SubmitTaskRequest request = new SubmitTaskRequest();
        request.setFileId(uploaded.getId());
        request.setNote(note);
        return taskService.submitTask(taskId, request, currentEmail);
    }

}
