package com.smartlab.service.impl;

import com.smartlab.dto.request.AddAssigneesRequest;
import com.smartlab.dto.request.AddAttachmentRequest;
import com.smartlab.dto.request.CreateTaskRequest;
import com.smartlab.dto.request.SubmitTaskRequest;
import com.smartlab.dto.request.UpdateTaskRequest;
import com.smartlab.dto.response.TaskAssigneeResponse;
import com.smartlab.dto.response.TaskAttachmentResponse;
import com.smartlab.dto.response.TaskDetailResponse;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.entity.TaskAssigneeEntity;
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
import com.smartlab.service.NotificationService;
import com.smartlab.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {
    private static final long PROJECT_ID = 7L;
    private static final long TASK_ID = 11L;
    private static final long FILE_ID = 13L;
    private static final String EMAIL = "member@smartlab.test";

    @Mock private TaskRepository taskRepository;
    @Mock private TaskAssigneeRepository taskAssigneeRepository;
    @Mock private TaskAttachmentRepository taskAttachmentRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private StoredFileRepository storedFileRepository;
    @Mock private PermissionService permissionService;
    @Mock private NotificationService notificationService;
    @Mock private ProjectEntity project;

    @InjectMocks private TaskServiceImpl service;

    private UserEntity user;
    private TaskEntity task;

    @BeforeEach
    void setUp() {
        user = user(1L, EMAIL);
        task = TaskEntity.create(project, null, "Task", null, TaskStatus.TODO,
                TaskPriority.MEDIUM, null, null, user);
        ReflectionTestUtils.setField(task, "id", TASK_ID);
        when(project.getId()).thenReturn(PROJECT_ID);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(taskRepository.findByIdAndDeletedAtIsNull(TASK_ID)).thenReturn(Optional.of(task));
        when(permissionService.getRoleCodes(user)).thenReturn(Set.of());
    }

    @Test
    void addAttachmentAcceptsAnActiveFileOwnedByAnActiveProjectMember() {
        StoredFileEntity file = activeFile(FILE_ID, user);
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, user.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(true);
        when(storedFileRepository.findById(FILE_ID)).thenReturn(Optional.of(file));

        TaskAttachmentResponse response = service.addAttachment(TASK_ID, attachmentRequest(FILE_ID), EMAIL);

        assertThat(response.getFileId()).isEqualTo(FILE_ID);
        ArgumentCaptor<com.smartlab.entity.TaskAttachmentEntity> attachment =
                ArgumentCaptor.forClass(com.smartlab.entity.TaskAttachmentEntity.class);
        verify(taskAttachmentRepository).save(attachment.capture());
        assertThat(attachment.getValue().getFile()).isSameAs(file);
        assertThat(attachment.getValue().getUploadedBy()).isSameAs(user);
    }

    @Test
    void addAttachmentRejectsAFileOwnedByAnotherUser() {
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, user.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(true);
        when(storedFileRepository.findById(FILE_ID)).thenReturn(Optional.of(activeFile(FILE_ID, user(2L, "other@smartlab.test"))));

        assertStatus(() -> service.addAttachment(TASK_ID, attachmentRequest(FILE_ID), EMAIL), HttpStatus.FORBIDDEN);

        verify(taskAttachmentRepository, never()).save(any());
    }

    @Test
    void addAttachmentTreatsSoftDeletedFilesAsNotFound() {
        StoredFileEntity deletedFile = activeFile(FILE_ID, user);
        deletedFile.setDeletedAt(java.time.Instant.now());
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, user.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(true);
        when(storedFileRepository.findById(FILE_ID)).thenReturn(Optional.of(deletedFile));

        assertStatus(() -> service.addAttachment(TASK_ID, attachmentRequest(FILE_ID), EMAIL), HttpStatus.NOT_FOUND);

        verify(taskAttachmentRepository, never()).save(any());
    }

    @Test
    void addAttachmentRejectsAPrivateFile() {
        StoredFileEntity privateFile = activeFile(FILE_ID, user);
        privateFile.setAccessScope("PRIVATE");
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, user.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(true);
        when(storedFileRepository.findById(FILE_ID)).thenReturn(Optional.of(privateFile));

        assertStatus(() -> service.addAttachment(TASK_ID, attachmentRequest(FILE_ID), EMAIL), HttpStatus.BAD_REQUEST);

        verify(taskAttachmentRepository, never()).save(any());
    }

    @Test
    void addAttachmentRejectsAFileFromAnotherProject() {
        StoredFileEntity otherProjectFile = activeFile(FILE_ID, user);
        otherProjectFile.setProjectId(99L);
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, user.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(true);
        when(storedFileRepository.findById(FILE_ID)).thenReturn(Optional.of(otherProjectFile));

        assertStatus(() -> service.addAttachment(TASK_ID, attachmentRequest(FILE_ID), EMAIL), HttpStatus.BAD_REQUEST);

        verify(taskAttachmentRepository, never()).save(any());
    }

    @Test
    void addAttachmentRejectsNonMembersBeforeLookingUpTheFile() {
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, user.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(false);

        assertStatus(() -> service.addAttachment(TASK_ID, attachmentRequest(FILE_ID), EMAIL), HttpStatus.FORBIDDEN);

        verifyNoInteractions(storedFileRepository);
        verify(taskAttachmentRepository, never()).save(any());
    }

    @Test
    void attachmentUploadPrecheckAllowsAnActiveMemberWithoutTaskRead() {
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, user.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(true);

        assertThat(service.requireAttachmentUploadProjectId(TASK_ID, EMAIL)).isEqualTo(PROJECT_ID);

        verify(permissionService, never()).getEffectivePermissionCodes(user);
    }

    @Test
    void getDetailAllowsAnActiveMemberWithTaskRead() {
        when(permissionService.getEffectivePermissionCodes(user)).thenReturn(Set.of("TASK_READ"));
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, user.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(true);
        when(taskAssigneeRepository.findAllByTask_Id(TASK_ID)).thenReturn(List.of());
        when(taskAttachmentRepository.findAllByTaskId(TASK_ID)).thenReturn(List.of());

        TaskDetailResponse response = service.getDetail(TASK_ID, EMAIL);

        assertThat(response.getId()).isEqualTo(TASK_ID);
    }

    @Test
    void getDetailRejectsAnActiveMemberWithoutTaskRead() {
        when(permissionService.getEffectivePermissionCodes(user)).thenReturn(Set.of());

        assertStatus(() -> service.getDetail(TASK_ID, EMAIL), HttpStatus.FORBIDDEN);

        verify(projectMemberRepository, never()).existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, user.getId(), ProjectMemberStatus.ACTIVE);
    }

    @Test
    void getDetailRejectsTaskReadUserWhoIsNotAnActiveProjectMember() {
        when(permissionService.getEffectivePermissionCodes(user)).thenReturn(Set.of("TASK_READ"));
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, user.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(false);

        assertStatus(() -> service.getDetail(TASK_ID, EMAIL), HttpStatus.FORBIDDEN);
    }

    @Test
    void ordinaryMemberWithoutTaskManagementCapabilityCannotDeleteTask() {
        when(permissionService.getEffectivePermissionCodes(user)).thenReturn(Set.of());
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                PROJECT_ID, user.getId(), ProjectRole.LEADER, ProjectMemberStatus.ACTIVE)).thenReturn(false);

        assertStatus(() -> service.delete(TASK_ID, EMAIL), HttpStatus.FORBIDDEN);

        assertThat(task.getDeletedAt()).isNull();
    }

    @Test
    void submitTaskRejectsAnotherUsersFileEvenForAnAssignee() {
        when(taskAssigneeRepository.existsById_TaskIdAndId_UserId(TASK_ID, user.getId())).thenReturn(true);
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, user.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(true);
        when(permissionService.getEffectivePermissionCodes(user)).thenReturn(Set.of());
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                PROJECT_ID, user.getId(), ProjectRole.LEADER, ProjectMemberStatus.ACTIVE)).thenReturn(false);
        when(storedFileRepository.findById(FILE_ID)).thenReturn(Optional.of(activeFile(FILE_ID, user(2L, "other@smartlab.test"))));

        assertStatus(() -> service.submitTask(TASK_ID, submitRequest(FILE_ID), EMAIL), HttpStatus.FORBIDDEN);

        verify(taskAttachmentRepository, never()).save(any());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void submitTaskRejectsARemovedAssignee() {
        when(taskAssigneeRepository.existsById_TaskIdAndId_UserId(TASK_ID, user.getId())).thenReturn(true);
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, user.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(false);
        when(permissionService.getEffectivePermissionCodes(user)).thenReturn(Set.of());
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                PROJECT_ID, user.getId(), ProjectRole.LEADER, ProjectMemberStatus.ACTIVE)).thenReturn(false);

        assertStatus(
                () -> service.submitTask(TASK_ID, submitRequest(FILE_ID), EMAIL),
                HttpStatus.FORBIDDEN
        );

        verifyNoInteractions(storedFileRepository);
        verify(taskAttachmentRepository, never()).save(any());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void submitTaskAcceptsAnActiveFileOwnedByAnAuthorizedAssignee() {
        StoredFileEntity file = activeFile(FILE_ID, user);
        when(taskAssigneeRepository.existsById_TaskIdAndId_UserId(TASK_ID, user.getId())).thenReturn(true);
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, user.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(true);
        when(permissionService.getEffectivePermissionCodes(user)).thenReturn(Set.of());
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                PROJECT_ID, user.getId(), ProjectRole.LEADER, ProjectMemberStatus.ACTIVE)).thenReturn(false);
        when(storedFileRepository.findById(FILE_ID)).thenReturn(Optional.of(file));
        when(taskAssigneeRepository.findAllByTask_Id(TASK_ID)).thenReturn(List.of());
        when(taskAttachmentRepository.findAllByTaskId(TASK_ID)).thenReturn(List.of());

        TaskDetailResponse response = service.submitTask(TASK_ID, submitRequest(FILE_ID), EMAIL);

        assertThat(response.getStatus()).isEqualTo(TaskStatus.REVIEW.name());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.REVIEW);
        verify(taskAttachmentRepository).save(any());
    }

    @Test
    void submissionUploadPrecheckAllowsAnActiveAssigneeWithoutTaskManage() {
        when(taskAssigneeRepository.existsById_TaskIdAndId_UserId(TASK_ID, user.getId())).thenReturn(true);
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, user.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(true);
        when(permissionService.getEffectivePermissionCodes(user)).thenReturn(Set.of());
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                PROJECT_ID, user.getId(), ProjectRole.LEADER, ProjectMemberStatus.ACTIVE)).thenReturn(false);

        assertThat(service.requireSubmissionUploadProjectId(TASK_ID, EMAIL)).isEqualTo(PROJECT_ID);
    }

    @Test
    void submissionUploadPrecheckAllowsATaskManagerWhoIsNotAnAssignee() {
        when(taskAssigneeRepository.existsById_TaskIdAndId_UserId(TASK_ID, user.getId())).thenReturn(false);
        when(permissionService.getEffectivePermissionCodes(user)).thenReturn(Set.of("TASK_MANAGE"));

        assertThat(service.requireSubmissionUploadProjectId(TASK_ID, EMAIL)).isEqualTo(PROJECT_ID);
    }

    @Test
    void updateSendsNotificationToCurrentAssigneesWhenTaskChanges() {
        UserEntity member = user(2L, "assignee@smartlab.test");
        when(permissionService.getRoleCodes(user)).thenReturn(Set.of("ADMIN"));
        when(taskAssigneeRepository.findAllByTask_Id(TASK_ID))
                .thenReturn(List.of(TaskAssigneeEntity.of(task, member)));

        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setDescription("Updated description");

        service.update(TASK_ID, request, EMAIL);

        verify(notificationService).notify(
                eq(member.getId()),
                eq("TASK_UPDATED"),
                eq("Nhiệm vụ đã được cập nhật: " + task.getTitle()),
                any(),
                any()
        );
    }

    @Test
    void updateNotifiesEachDistinctAssigneeOnce() {
        UserEntity first = user(2L, "first@smartlab.test");
        UserEntity second = user(3L, "second@smartlab.test");
        when(permissionService.getRoleCodes(user)).thenReturn(Set.of("ADMIN"));
        when(taskAssigneeRepository.findAllByTask_Id(TASK_ID))
                .thenReturn(List.of(
                        TaskAssigneeEntity.of(task, first),
                        TaskAssigneeEntity.of(task, first),
                        TaskAssigneeEntity.of(task, second)
                ));

        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setDescription("Updated description");

        service.update(TASK_ID, request, EMAIL);

        verify(notificationService).notify(
                eq(first.getId()), eq("TASK_UPDATED"), any(), any(), any()
        );
        verify(notificationService).notify(
                eq(second.getId()), eq("TASK_UPDATED"), any(), any(), any()
        );
    }

    @Test
    void updateDoesNotNotifyActorWhenActorIsAlsoAssignee() {
        UserEntity member = user(2L, "member@smartlab.test");
        when(permissionService.getRoleCodes(user)).thenReturn(Set.of("ADMIN"));
        when(taskAssigneeRepository.findAllByTask_Id(TASK_ID))
                .thenReturn(List.of(
                        TaskAssigneeEntity.of(task, user),
                        TaskAssigneeEntity.of(task, member)
                ));

        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setDescription("Updated description");

        service.update(TASK_ID, request, EMAIL);

        verify(notificationService, never()).notify(
                eq(user.getId()), any(), any(), any(), any()
        );
        verify(notificationService).notify(
                eq(member.getId()), eq("TASK_UPDATED"), any(), any(), any()
        );
    }

    @Test
    void updateDoesNotSendNotificationForNoOpRequest() {
        when(permissionService.getRoleCodes(user)).thenReturn(Set.of("ADMIN"));

        UpdateTaskRequest request = new UpdateTaskRequest();

        service.update(TASK_ID, request, EMAIL);

        verify(taskAssigneeRepository).findAllByTask_Id(TASK_ID);
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void addAssigneesSendsNotificationToNewAssignee() {
        UserEntity member = user(2L, "assignee@smartlab.test");
        when(permissionService.getRoleCodes(user)).thenReturn(Set.of("ADMIN"));
        when(taskAssigneeRepository.existsById_TaskIdAndId_UserId(TASK_ID, 2L)).thenReturn(false);
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, 2L, ProjectMemberStatus.ACTIVE)).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(taskAssigneeRepository.findAllByTask_Id(TASK_ID)).thenReturn(List.of(TaskAssigneeEntity.of(task, member)));

        AddAssigneesRequest request = new AddAssigneesRequest();
        request.setUserIds(List.of(2L));

        List<TaskAssigneeResponse> response = service.addAssignees(TASK_ID, request, EMAIL);

        assertThat(response).hasSize(1);
        verify(taskAssigneeRepository).save(any(TaskAssigneeEntity.class));
        verify(notificationService).notify(
                eq(2L),
                eq("TASK_ASSIGNED"),
                eq("Bạn được giao nhiệm vụ: " + task.getTitle()),
                any(),
                any()
        );
    }

    @Test
    void addAssigneesSkipsExistingAssigneeAndDoesNotSendDuplicateNotification() {
        when(permissionService.getRoleCodes(user)).thenReturn(Set.of("ADMIN"));
        when(taskAssigneeRepository.existsById_TaskIdAndId_UserId(TASK_ID, 2L)).thenReturn(true);
        when(taskAssigneeRepository.findAllByTask_Id(TASK_ID)).thenReturn(List.of());

        AddAssigneesRequest request = new AddAssigneesRequest();
        request.setUserIds(List.of(2L));

        service.addAssignees(TASK_ID, request, EMAIL);

        verify(taskAssigneeRepository, never()).save(any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void addAssigneesRejectsNonActiveProjectMember() {
        when(permissionService.getRoleCodes(user)).thenReturn(Set.of("ADMIN"));
        when(taskAssigneeRepository.existsById_TaskIdAndId_UserId(TASK_ID, 3L)).thenReturn(false);
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, 3L, ProjectMemberStatus.ACTIVE)).thenReturn(false);

        AddAssigneesRequest request = new AddAssigneesRequest();
        request.setUserIds(List.of(3L));

        assertStatus(() -> service.addAssignees(TASK_ID, request, EMAIL), HttpStatus.BAD_REQUEST);

        verify(taskAssigneeRepository, never()).save(any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void addAssigneesRejectsNonLeaderAndNonAdmin() {
        when(permissionService.getRoleCodes(user)).thenReturn(Set.of());
        when(permissionService.getEffectivePermissionCodes(user)).thenReturn(Set.of());
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                PROJECT_ID, user.getId(), ProjectRole.LEADER, ProjectMemberStatus.ACTIVE)).thenReturn(false);

        AddAssigneesRequest request = new AddAssigneesRequest();
        request.setUserIds(List.of(2L));

        assertStatus(() -> service.addAssignees(TASK_ID, request, EMAIL), HttpStatus.FORBIDDEN);

        verify(taskAssigneeRepository, never()).save(any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    private static AddAttachmentRequest attachmentRequest(long fileId) {
        AddAttachmentRequest request = new AddAttachmentRequest();
        request.setFileId(fileId);
        request.setAttachmentType(AttachmentType.REFERENCE);
        return request;
    }

    private static SubmitTaskRequest submitRequest(long fileId) {
        SubmitTaskRequest request = new SubmitTaskRequest();
        request.setFileId(fileId);
        return request;
    }

    private static StoredFileEntity activeFile(long id, UserEntity owner) {
        return StoredFileEntity.builder()
                .id(id).ownerUser(owner).storageProvider("local").storageKey("files/" + id)
                .originalName("result.pdf").mimeType("application/pdf").sizeBytes(100L)
                .projectId(PROJECT_ID).accessScope("PROJECT").build();
    }

    private static UserEntity user(long id, String email) {
        return UserEntity.builder().id(id).userId("user-" + id).name("User " + id)
                .email(email).password("encoded").isActive(true).isAccountVerified(true).build();
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, HttpStatus status) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(status));
    }
}
