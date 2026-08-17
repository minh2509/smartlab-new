package com.smartlab.service.impl;

import com.smartlab.dto.response.FileResponse;
import com.smartlab.dto.response.TaskAttachmentResponse;
import com.smartlab.enums.AttachmentType;
import com.smartlab.service.FileService;
import com.smartlab.service.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskFileWorkflowServiceImplTest {
    private static final long TASK_ID = 11L;
    private static final long PROJECT_ID = 7L;
    private static final long FILE_ID = 13L;
    private static final String EMAIL = "member@smartlab.test";

    @Mock private FileService fileService;
    @Mock private TaskService taskService;
    @InjectMocks private TaskFileWorkflowServiceImpl service;

    private final MockMultipartFile upload = new MockMultipartFile(
            "file", "task.txt", "text/plain", "task".getBytes());

    @Test
    void attachmentWorkflowUploadsProjectFileForTaskProjectThenAddsAttachment() {
        TaskAttachmentResponse expected = TaskAttachmentResponse.builder().fileId(FILE_ID).build();
        when(taskService.requireAttachmentUploadProjectId(TASK_ID, EMAIL)).thenReturn(PROJECT_ID);
        when(fileService.uploadForTaskProject(upload, "Task attachment", EMAIL, PROJECT_ID))
                .thenReturn(FileResponse.builder().id(FILE_ID).build());
        when(taskService.addAttachment(eq(TASK_ID), any(), eq(EMAIL))).thenReturn(expected);

        TaskAttachmentResponse response = service.uploadAndAddAttachment(
                TASK_ID, upload, AttachmentType.REFERENCE, "Task attachment", EMAIL);

        assertThat(response).isSameAs(expected);
        ArgumentCaptor<com.smartlab.dto.request.AddAttachmentRequest> request =
                ArgumentCaptor.forClass(com.smartlab.dto.request.AddAttachmentRequest.class);
        verify(taskService).requireAttachmentUploadProjectId(TASK_ID, EMAIL);
        verify(fileService).uploadForTaskProject(upload, "Task attachment", EMAIL, PROJECT_ID);
        verify(taskService).addAttachment(eq(TASK_ID), request.capture(), eq(EMAIL));
        assertThat(request.getValue().getFileId()).isEqualTo(FILE_ID);
        assertThat(request.getValue().getAttachmentType()).isEqualTo(AttachmentType.REFERENCE);
        verify(fileService, never()).delete(any(), any(), any());
    }

    @Test
    void attachmentWorkflowPropagatesTaskMutationFailureAfterUpload() {
        ResponseStatusException failure = new ResponseStatusException(HttpStatus.FORBIDDEN, "Denied");
        when(taskService.requireAttachmentUploadProjectId(TASK_ID, EMAIL)).thenReturn(PROJECT_ID);
        when(fileService.uploadForTaskProject(upload, "Task attachment", EMAIL, PROJECT_ID))
                .thenReturn(FileResponse.builder().id(FILE_ID).build());
        when(taskService.addAttachment(eq(TASK_ID), any(), eq(EMAIL))).thenThrow(failure);

        assertThatThrownBy(() -> service.uploadAndAddAttachment(
                TASK_ID, upload, AttachmentType.INPUT, "Task attachment", EMAIL))
                .isSameAs(failure);

        verify(fileService).uploadForTaskProject(upload, "Task attachment", EMAIL, PROJECT_ID);
        verify(fileService, never()).delete(any(), any(), any());
    }

    @Test
    void submissionWorkflowPropagatesTaskMutationFailureAfterUpload() {
        ResponseStatusException failure = new ResponseStatusException(HttpStatus.FORBIDDEN, "Denied");
        when(taskService.requireSubmissionUploadProjectId(TASK_ID, EMAIL)).thenReturn(PROJECT_ID);
        when(fileService.uploadForTaskProject(upload, "Task Submission Result", EMAIL, PROJECT_ID))
                .thenReturn(FileResponse.builder().id(FILE_ID).build());
        when(taskService.submitTask(eq(TASK_ID), any(), eq(EMAIL))).thenThrow(failure);

        assertThatThrownBy(() -> service.uploadAndSubmit(TASK_ID, upload, "Result note", EMAIL))
                .isSameAs(failure);

        verify(taskService).requireSubmissionUploadProjectId(TASK_ID, EMAIL);
        verify(fileService).uploadForTaskProject(upload, "Task Submission Result", EMAIL, PROJECT_ID);
        verify(fileService, never()).delete(any(), any(), any());
    }
}
