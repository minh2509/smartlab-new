package com.smartlab.service;

import com.smartlab.dto.request.AddAssigneesRequest;
import com.smartlab.dto.request.AddAttachmentRequest;
import com.smartlab.dto.request.CreateTaskRequest;
import com.smartlab.dto.request.SubmitTaskRequest;
import com.smartlab.dto.request.UpdateTaskRequest;
import com.smartlab.dto.response.TaskAssigneeResponse;
import com.smartlab.dto.response.TaskAttachmentResponse;
import com.smartlab.dto.response.TaskDetailResponse;
import com.smartlab.dto.response.TaskSummaryResponse;
import com.smartlab.enums.TaskPriority;
import com.smartlab.enums.TaskStatus;
import org.springframework.data.domain.Page;

import java.util.List;

public interface TaskService {

    Page<TaskSummaryResponse> listByProject(
            Long projectId,
            TaskStatus status,
            TaskPriority priority,
            Long assigneeUserId,
            int page,
            int size,
            String currentEmail
    );

    TaskDetailResponse getDetail(Long taskId, String currentEmail);

    TaskDetailResponse create(Long projectId, CreateTaskRequest request, String currentEmail);

    TaskDetailResponse update(Long taskId, UpdateTaskRequest request, String currentEmail);

    void delete(Long taskId, String currentEmail);

    List<TaskAssigneeResponse> addAssignees(Long taskId, AddAssigneesRequest request, String currentEmail);

    void removeAssignee(Long taskId, Long userId, String currentEmail);

    TaskAttachmentResponse addAttachment(Long taskId, AddAttachmentRequest request, String currentEmail);

    Long requireAttachmentUploadProjectId(Long taskId, String currentEmail);

    TaskDetailResponse submitTask(Long taskId, SubmitTaskRequest request, String currentEmail);

    Long requireSubmissionUploadProjectId(Long taskId, String currentEmail);
}
