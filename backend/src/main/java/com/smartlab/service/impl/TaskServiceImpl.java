package com.smartlab.service.impl;

import com.smartlab.dto.request.AddAssigneesRequest;
import com.smartlab.dto.request.AddAttachmentRequest;
import com.smartlab.dto.request.CreateTaskRequest;
import com.smartlab.dto.request.SubmitTaskRequest;
import com.smartlab.dto.request.UpdateTaskRequest;
import com.smartlab.dto.response.TaskAssigneeResponse;
import com.smartlab.dto.response.TaskAttachmentResponse;
import com.smartlab.dto.response.TaskDetailResponse;
import com.smartlab.dto.response.TaskSummaryResponse;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.entity.TaskAssigneeEntity;
import com.smartlab.entity.TaskAttachmentEntity;
import com.smartlab.entity.TaskEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.AttachmentType;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import com.smartlab.enums.TaskPriority;
import com.smartlab.enums.TaskStatus;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.StoredFileRepository;
import com.smartlab.repo.TaskAssigneeRepository;
import com.smartlab.repo.TaskAttachmentRepository;
import com.smartlab.repo.TaskRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.PermissionService;
import com.smartlab.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private static final String ADMIN = "ADMIN";
    private static final String TASK_READ = "TASK_READ";
    private static final String TASK_MANAGE = "TASK_MANAGE";

    private final TaskRepository taskRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final StoredFileRepository storedFileRepository;
    private final PermissionService permissionService;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private UserEntity requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private ProjectEntity requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private TaskEntity requireTask(Long taskId) {
        return taskRepository.findByIdAndDeletedAtIsNull(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private StoredFileEntity requireOwnedProjectFile(Long fileId, UserEntity user, Long projectId) {
        StoredFileEntity file = storedFileRepository.findById(fileId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));

        if (file.getOwnerUser() == null || !file.getOwnerUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only attach your own files");
        }
        if (!"PROJECT".equals(file.getAccessScope()) || !projectId.equals(file.getProjectId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Task attachments must use PROJECT scope for the same project"
            );
        }

        return file;
    }

    /** Admin, project LEADER, or user with TASK_MANAGE permission can manage tasks */
    private void requireTaskManageAccess(UserEntity user, Long projectId) {
        Set<String> roles = permissionService.getRoleCodes(user);
        if (roles.contains(ADMIN)) return;
        Set<String> perms = permissionService.getEffectivePermissionCodes(user);
        if (perms.contains(TASK_MANAGE)) return;
        boolean isLeader = projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                projectId, user.getId(), ProjectRole.LEADER, ProjectMemberStatus.ACTIVE
        );
        if (!isLeader) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        }
    }

    /** Any ACTIVE project member can view tasks */
    private void requireProjectMemberAccess(UserEntity user, Long projectId) {
        Set<String> roles = permissionService.getRoleCodes(user);
        if (roles.contains(ADMIN)) return;
        boolean isMember = projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                projectId, user.getId(), ProjectMemberStatus.ACTIVE
        );
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this project");
        }
    }

    private void requireSubmissionAccess(UserEntity user, TaskEntity task) {
        Long projectId = task.getProject().getId();
        boolean isAssignee = taskAssigneeRepository.existsById_TaskIdAndId_UserId(task.getId(), user.getId());
        boolean isActiveAssignee = isAssignee
                && projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                        projectId, user.getId(), ProjectMemberStatus.ACTIVE
                );

        boolean isManager = false;
        try {
            requireTaskManageAccess(user, projectId);
            isManager = true;
        } catch (ResponseStatusException ignored) { }

        if (!isActiveAssignee && !isManager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only active assignees or managers can submit this task");
        }
    }

    /** Admins, or ACTIVE members with TASK_READ, may read tasks. */
    private void requireTaskReadAccess(UserEntity user, Long projectId) {
        Set<String> roles = permissionService.getRoleCodes(user);
        if (roles.contains(ADMIN)) return;
        Set<String> permissions = permissionService.getEffectivePermissionCodes(user);
        if (!permissions.contains(TASK_READ)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        }
        boolean isMember = projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                projectId, user.getId(), ProjectMemberStatus.ACTIVE
        );
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this project");
        }
    }

    private TaskAssigneeResponse toAssigneeResponse(TaskAssigneeEntity a) {
        return TaskAssigneeResponse.builder()
                .userId(a.getUser().getId())
                .accountUserId(a.getUser().getUserId())
                .name(a.getUser().getName())
                .email(a.getUser().getEmail())
                .assignedAt(a.getAssignedAt())
                .build();
    }

    private TaskAttachmentResponse toAttachmentResponse(TaskAttachmentEntity a) {
        UserEntity uploader = a.getUploadedBy();
        return TaskAttachmentResponse.builder()
                .id(a.getId())
                .fileId(a.getFile().getId())
                .originalName(a.getFile().getOriginalName())
                .mimeType(a.getFile().getMimeType())
                .sizeBytes(a.getFile().getSizeBytes())
                .attachmentType(a.getAttachmentType().name())
                .description(a.getDescription())
                .uploadedByUserId(uploader != null ? uploader.getId() : null)
                .uploadedByName(uploader != null ? uploader.getName() : null)
                .createdAt(a.getCreatedAt())
                .build();
    }

    private TaskDetailResponse toDetailResponse(TaskEntity task) {
        List<TaskAssigneeEntity> assignees = taskAssigneeRepository.findAllByTask_Id(task.getId());
        List<TaskAttachmentEntity> attachments = taskAttachmentRepository.findAllByTaskId(task.getId());
        UserEntity creator = task.getCreatedBy();
        return TaskDetailResponse.builder()
                .id(task.getId())
                .projectId(task.getProject().getId())
                .parentTaskId(task.getParentTask() != null ? task.getParentTask().getId() : null)
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus().name())
                .priority(task.getPriority().name())
                .startAt(task.getStartAt())
                .dueAt(task.getDueAt())
                .createdByUserId(creator != null ? creator.getId() : null)
                .createdByName(creator != null ? creator.getName() : null)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .assignees(assignees.stream().map(this::toAssigneeResponse).toList())
                .attachments(attachments.stream().map(this::toAttachmentResponse).toList())
                .build();
    }

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<TaskSummaryResponse> listByProject(
            Long projectId,
            TaskStatus status,
            TaskPriority priority,
            Long assigneeUserId,
            int page,
            int size,
            String currentEmail
    ) {
        UserEntity user = currentEmail != null ? userRepository.findByEmail(currentEmail).orElse(null) : null;
        if (user != null) {
            requireTaskReadAccess(user, projectId);
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Page<TaskEntity> taskPage = taskRepository.findByProjectFiltered(
                projectId, status, priority, assigneeUserId, PageRequest.of(page, size)
        );

        List<Long> taskIds = taskPage.getContent().stream().map(TaskEntity::getId).toList();
        Map<Long, List<TaskAssigneeEntity>> assigneeMap = taskAssigneeRepository
                .findAllByTaskIds(taskIds)
                .stream()
                .collect(Collectors.groupingBy(a -> a.getTask().getId()));

        Map<Long, Long> attachCountMap = taskAttachmentRepository.findAll()
                .stream()
                .filter(a -> taskIds.contains(a.getTask().getId()))
                .collect(Collectors.groupingBy(a -> a.getTask().getId(), Collectors.counting()));

        return taskPage.map(task -> {
            List<TaskAssigneeEntity> assignees = assigneeMap.getOrDefault(task.getId(), List.of());
            long attachCount = attachCountMap.getOrDefault(task.getId(), 0L);
            return TaskSummaryResponse.builder()
                    .id(task.getId())
                    .projectId(task.getProject().getId())
                    .parentTaskId(task.getParentTask() != null ? task.getParentTask().getId() : null)
                    .title(task.getTitle())
                    .status(task.getStatus().name())
                    .priority(task.getPriority().name())
                    .startAt(task.getStartAt())
                    .dueAt(task.getDueAt())
                    .createdAt(task.getCreatedAt())
                    .updatedAt(task.getUpdatedAt())
                    .assignees(assignees.stream().map(this::toAssigneeResponse).toList())
                    .attachmentCount((int) attachCount)
                    .build();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public TaskDetailResponse getDetail(Long taskId, String currentEmail) {
        UserEntity user = requireUser(currentEmail);
        TaskEntity task = requireTask(taskId);
        requireTaskReadAccess(user, task.getProject().getId());
        return toDetailResponse(task);
    }

    @Override
    @Transactional
    public TaskDetailResponse create(Long projectId, CreateTaskRequest request, String currentEmail) {
        UserEntity user = requireUser(currentEmail);
        ProjectEntity project = requireProject(projectId);
        requireTaskManageAccess(user, projectId);

        TaskEntity parentTask = null;
        if (request.getParentTaskId() != null) {
            parentTask = requireTask(request.getParentTaskId());
            if (!parentTask.getProject().getId().equals(projectId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent task belongs to a different project");
            }
        }

        TaskStatus status = request.getStatus() != null ? request.getStatus() : TaskStatus.TODO;
        TaskEntity task = TaskEntity.create(
                project,
                parentTask,
                request.getTitle(),
                request.getDescription(),
                status,
                request.getPriority(),
                request.getStartAt(),
                request.getDueAt(),
                user
        );
        taskRepository.save(task);
        return toDetailResponse(task);
    }

    @Override
    @Transactional
    public TaskDetailResponse update(Long taskId, UpdateTaskRequest request, String currentEmail) {
        UserEntity user = requireUser(currentEmail);
        TaskEntity task = requireTask(taskId);
        requireTaskManageAccess(user, task.getProject().getId());

        TaskEntity parentTask = null;
        if (request.getParentTaskId() != null) {
            parentTask = requireTask(request.getParentTaskId());
            if (!parentTask.getProject().getId().equals(task.getProject().getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent task belongs to a different project");
            }
        }

        task.update(
                request.getTitle() != null ? request.getTitle() : task.getTitle(),
                request.getDescription() != null ? request.getDescription() : task.getDescription(),
                request.getStatus() != null ? request.getStatus() : task.getStatus(),
                request.getPriority() != null ? request.getPriority() : task.getPriority(),
                request.getStartAt() != null ? request.getStartAt() : task.getStartAt(),
                request.getDueAt() != null ? request.getDueAt() : task.getDueAt(),
                parentTask != null ? parentTask : task.getParentTask()
        );
        return toDetailResponse(task);
    }

    @Override
    @Transactional
    public void delete(Long taskId, String currentEmail) {
        UserEntity user = requireUser(currentEmail);
        TaskEntity task = requireTask(taskId);
        requireTaskManageAccess(user, task.getProject().getId());
        task.softDelete();
    }

    // -------------------------------------------------------------------------
    // Assignees
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public List<TaskAssigneeResponse> addAssignees(Long taskId, AddAssigneesRequest request, String currentEmail) {
        UserEntity currentUser = requireUser(currentEmail);
        TaskEntity task = requireTask(taskId);
        requireTaskManageAccess(currentUser, task.getProject().getId());

        for (Long userId : request.getUserIds()) {
            if (taskAssigneeRepository.existsById_TaskIdAndId_UserId(taskId, userId)) {
                continue; // already assigned, skip
            }
            boolean isMember = projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                    task.getProject().getId(), userId, ProjectMemberStatus.ACTIVE
            );
            if (!isMember) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "User " + userId + " is not an active member of this project"
                );
            }
            UserEntity assignee = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));
            taskAssigneeRepository.save(TaskAssigneeEntity.of(task, assignee));
        }

        return taskAssigneeRepository.findAllByTask_Id(taskId)
                .stream().map(this::toAssigneeResponse).toList();
    }

    @Override
    @Transactional
    public void removeAssignee(Long taskId, Long userId, String currentEmail) {
        UserEntity currentUser = requireUser(currentEmail);
        TaskEntity task = requireTask(taskId);
        requireTaskManageAccess(currentUser, task.getProject().getId());
        if (!taskAssigneeRepository.existsById_TaskIdAndId_UserId(taskId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignee not found");
        }
        taskAssigneeRepository.deleteById_TaskIdAndId_UserId(taskId, userId);
    }

    // -------------------------------------------------------------------------
    // Attachments
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public TaskAttachmentResponse addAttachment(Long taskId, AddAttachmentRequest request, String currentEmail) {
        UserEntity user = requireUser(currentEmail);
        TaskEntity task = requireTask(taskId);
        requireProjectMemberAccess(user, task.getProject().getId());

        StoredFileEntity file = requireOwnedProjectFile(request.getFileId(), user, task.getProject().getId());

        TaskAttachmentEntity attachment = TaskAttachmentEntity.create(
                task, file, request.getAttachmentType(), request.getDescription(), user
        );
        taskAttachmentRepository.save(attachment);
        return toAttachmentResponse(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public Long requireAttachmentUploadProjectId(Long taskId, String currentEmail) {
        UserEntity user = requireUser(currentEmail);
        TaskEntity task = requireTask(taskId);
        requireProjectMemberAccess(user, task.getProject().getId());
        return task.getProject().getId();
    }

    @Override
    @Transactional
    public TaskDetailResponse submitTask(Long taskId, SubmitTaskRequest request, String currentEmail) {
        UserEntity user = requireUser(currentEmail);
        TaskEntity task = requireTask(taskId);
        requireSubmissionAccess(user, task);

        StoredFileEntity file = requireOwnedProjectFile(request.getFileId(), user, task.getProject().getId());

        TaskAttachmentEntity attachment = TaskAttachmentEntity.create(
                task, file, AttachmentType.RESULT, request.getNote(), user
        );
        taskAttachmentRepository.save(attachment);
        task.transitionStatus(TaskStatus.REVIEW);
        return toDetailResponse(task);
    }

    @Override
    @Transactional(readOnly = true)
    public Long requireSubmissionUploadProjectId(Long taskId, String currentEmail) {
        UserEntity user = requireUser(currentEmail);
        TaskEntity task = requireTask(taskId);
        requireSubmissionAccess(user, task);
        return task.getProject().getId();
    }
}
