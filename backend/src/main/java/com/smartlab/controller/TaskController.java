package com.smartlab.controller;

import com.smartlab.dto.request.AddAssigneesRequest;
import com.smartlab.dto.request.AddAttachmentRequest;
import com.smartlab.dto.request.CreateTaskRequest;
import com.smartlab.dto.request.SubmitTaskRequest;
import com.smartlab.dto.request.UpdateTaskRequest;
import com.smartlab.dto.response.TaskAssigneeResponse;
import com.smartlab.dto.response.TaskAttachmentResponse;
import com.smartlab.dto.response.TaskDetailResponse;
import com.smartlab.dto.response.TaskSummaryResponse;
import com.smartlab.enums.AttachmentType;
import com.smartlab.enums.TaskPriority;
import com.smartlab.enums.TaskStatus;
import com.smartlab.service.TaskService;
import com.smartlab.service.TaskFileWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Task management within projects (D4)")
public class TaskController {

    private final TaskService taskService;
    private final TaskFileWorkflowService taskFileWorkflowService;

    // -------------------------------------------------------------------------
    // List / Get
    // -------------------------------------------------------------------------

    @GetMapping("/projects/{projectId}/tasks")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List tasks of a project", description = "Supports filtering by status, priority and assignee. Paginated.")
    public Page<TaskSummaryResponse> list(
            @PathVariable Long projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) Long assigneeUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return taskService.listByProject(projectId, status, priority, assigneeUserId, page, size, currentEmail);
    }

    @GetMapping("/tasks/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get task detail", description = "Returns full task info including assignees and attachments.")
    public TaskDetailResponse get(
            @PathVariable Long id,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return taskService.getDetail(id, currentEmail);
    }

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    @PostMapping("/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a task in a project", description = "Requires LEADER role or TASK_MANAGE permission in the project.")
    public TaskDetailResponse create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateTaskRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return taskService.create(projectId, request, currentEmail);
    }

    @PatchMapping("/tasks/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update a task", description = "All fields are optional – only supplied fields are updated.")
    public TaskDetailResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return taskService.update(id, request, currentEmail);
    }

    @DeleteMapping("/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Soft-delete a task")
    public void delete(
            @PathVariable Long id,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        taskService.delete(id, currentEmail);
    }

    // -------------------------------------------------------------------------
    // Assignees
    // -------------------------------------------------------------------------

    @PostMapping("/tasks/{id}/assignees")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add assignees to a task", description = "Users must be active members of the project.")
    public List<TaskAssigneeResponse> addAssignees(
            @PathVariable Long id,
            @Valid @RequestBody AddAssigneesRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return taskService.addAssignees(id, request, currentEmail);
    }

    @DeleteMapping("/tasks/{id}/assignees/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Remove an assignee from a task")
    public void removeAssignee(
            @PathVariable Long id,
            @PathVariable Long userId,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        taskService.removeAssignee(id, userId, currentEmail);
    }

    // -------------------------------------------------------------------------
    // Attachments & Submit
    // -------------------------------------------------------------------------

    @PostMapping("/tasks/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Attach a file to a task", description = "Attachment types: INPUT, RESULT, REFERENCE.")
    public TaskAttachmentResponse addAttachment(
            @PathVariable Long id,
            @Valid @RequestBody AddAttachmentRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return taskService.addAttachment(id, request, currentEmail);
    }

    @PostMapping(value = "/tasks/{id}/attachments/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('FILE_UPLOAD')")
    @Operation(summary = "Upload and attach a file to a task")
    public TaskAttachmentResponse uploadAttachment(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam AttachmentType attachmentType,
            @RequestParam(required = false) String description,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return taskFileWorkflowService.uploadAndAddAttachment(id, file, attachmentType, description, currentEmail);
    }

    @PostMapping("/tasks/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit task result", description = "Attaches a RESULT file and transitions task status to REVIEW.")
    public TaskDetailResponse submit(
            @PathVariable Long id,
            @Valid @RequestBody SubmitTaskRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return taskService.submitTask(id, request, currentEmail);
    }

    @PostMapping(value = "/tasks/{id}/submit/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('FILE_UPLOAD')")
    @Operation(summary = "Upload and submit a task result")
    public TaskDetailResponse uploadSubmission(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String note,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return taskFileWorkflowService.uploadAndSubmit(id, file, note, currentEmail);
    }
}
