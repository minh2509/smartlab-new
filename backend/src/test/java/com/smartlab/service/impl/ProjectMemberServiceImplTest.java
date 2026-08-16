package com.smartlab.service.impl;

import com.smartlab.dto.request.AddProjectMemberRequest;
import com.smartlab.dto.response.ProjectMemberResponse;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.ProjectMemberEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.AuditService;
import com.smartlab.service.NotificationRelated;
import com.smartlab.service.NotificationService;
import com.smartlab.service.PermissionService;
import com.smartlab.service.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMemberServiceImplTest {
    private static final String ACTOR_EMAIL = "leader@smartlab.test";

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;
    @Mock private PermissionService permissionService;

    @InjectMocks private ProjectMemberServiceImpl service;

    private ProjectEntity project;
    private UserEntity actor;
    private UserEntity member;

    @BeforeEach
    void setUp() {
        actor = user(1L, "leader-user", ACTOR_EMAIL, true);
        member = user(2L, "member-user", "member@smartlab.test", true);
        project = ProjectEntity.create(
                "SL-AI", "Smart Lab AI", null, null, ProjectType.RESEARCH, actor,
                ProjectStatus.IN_PROGRESS, null, null, null, false, false, actor
        );
        ReflectionTestUtils.setField(project, "id", 7L);
    }

    @Test
    void addsNewMemberWithAuditAndNotification() {
        stubManage();
        when(userRepository.findByUserId("member-user")).thenReturn(Optional.of(member));
        when(projectMemberRepository.findByProject_IdAndUser_Id(7L, 2L)).thenReturn(Optional.empty());
        when(projectMemberRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ProjectMemberEntity saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 55L);
            return saved;
        });

        ProjectMemberResponse response = service.add(7L, addRequest(" member-user "), ACTOR_EMAIL);

        assertThat(response.getUserId()).isEqualTo("member-user");
        assertThat(response.getProjectRole()).isEqualTo(ProjectRole.MEMBER);
        assertThat(response.getStatus()).isEqualTo(ProjectMemberStatus.ACTIVE);
        verify(auditService).log(eq("PROJECT_MEMBER_CREATED"), eq("PROJECT_MEMBER"), eq("55"), isNull(), anyMap());
        verify(notificationService).notify(
                eq(2L), eq("PROJECT_MEMBER_ADDED"), eq("You were added to project Smart Lab AI"),
                eq(new NotificationRelated(1L, "PROJECT", 7L, "/du-an/7")), any(Instant.class)
        );
    }

    @Test
    void reactivatesTheSameRemovedMembershipRow() {
        ProjectMemberEntity existing = ProjectMemberEntity.createMember(project, member);
        ReflectionTestUtils.setField(existing, "id", 55L);
        existing.remove();
        stubManage();
        when(userRepository.findByUserId("member-user")).thenReturn(Optional.of(member));
        when(projectMemberRepository.findByProject_IdAndUser_Id(7L, 2L)).thenReturn(Optional.of(existing));
        when(projectMemberRepository.saveAndFlush(existing)).thenReturn(existing);

        ProjectMemberResponse response = service.add(7L, addRequest("member-user"), ACTOR_EMAIL);

        assertThat(response.getStatus()).isEqualTo(ProjectMemberStatus.ACTIVE);
        assertThat(response.getRemovedAt()).isNull();
        verify(projectMemberRepository).saveAndFlush(existing);
        @SuppressWarnings("rawtypes") ArgumentCaptor<Map> before = ArgumentCaptor.forClass(Map.class);
        verify(auditService).log(eq("PROJECT_MEMBER_UPDATED"), eq("PROJECT_MEMBER"), eq("55"), before.capture(), anyMap());
        assertThat(before.getValue()).containsEntry("status", "REMOVED");
    }

    @Test
    void rejectsAlreadyActiveMemberWithoutSideEffects() {
        ProjectMemberEntity existing = ProjectMemberEntity.createMember(project, member);
        stubManage();
        when(userRepository.findByUserId("member-user")).thenReturn(Optional.of(member));
        when(projectMemberRepository.findByProject_IdAndUser_Id(7L, 2L)).thenReturn(Optional.of(existing));

        assertStatus(() -> service.add(7L, addRequest("member-user"), ACTOR_EMAIL), HttpStatus.CONFLICT);

        verify(projectMemberRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService, notificationService);
    }

    @Test
    void rejectsInactiveAccountBeforeCreatingMembership() {
        member.setIsActive(false);
        stubManage();
        when(userRepository.findByUserId("member-user")).thenReturn(Optional.of(member));

        assertStatus(() -> service.add(7L, addRequest("member-user"), ACTOR_EMAIL), HttpStatus.BAD_REQUEST);

        verify(projectMemberRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAccountWithAnInactiveAssignedGlobalRole() {
        stubManage();
        when(userRepository.findByUserId("member-user")).thenReturn(Optional.of(member));
        when(permissionService.hasInactiveAssignedRole(member)).thenReturn(true);

        assertStatus(() -> service.add(7L, addRequest("member-user"), ACTOR_EMAIL), HttpStatus.BAD_REQUEST);

        verify(projectMemberRepository, never()).saveAndFlush(any());
    }

    @Test
    void removesMemberByChangingStatusAndRetainingRow() {
        ProjectMemberEntity membership = ProjectMemberEntity.createMember(project, member);
        ReflectionTestUtils.setField(membership, "id", 55L);
        stubManage();
        when(userRepository.findByUserId("member-user")).thenReturn(Optional.of(member));
        when(projectMemberRepository.findByProject_IdAndUser_Id(7L, 2L)).thenReturn(Optional.of(membership));
        when(projectMemberRepository.saveAndFlush(membership)).thenReturn(membership);

        service.remove(7L, "member-user", ACTOR_EMAIL);

        assertThat(membership.getStatus()).isEqualTo(ProjectMemberStatus.REMOVED);
        assertThat(membership.getRemovedAt()).isNotNull();
        verify(projectMemberRepository, never()).delete(any());
        verify(auditService).log(eq("PROJECT_MEMBER_UPDATED"), eq("PROJECT_MEMBER"), eq("55"), anyMap(), anyMap());
        verify(notificationService).notify(
                eq(2L), eq("PROJECT_MEMBER_REMOVED"), any(), any(NotificationRelated.class), any(Instant.class)
        );
    }

    @Test
    void refusesToRemoveAnyActiveLeader() {
        ProjectMemberEntity leaderMembership = ProjectMemberEntity.createLeader(project, member);
        stubManage();
        when(userRepository.findByUserId("member-user")).thenReturn(Optional.of(member));
        when(projectMemberRepository.findByProject_IdAndUser_Id(7L, 2L)).thenReturn(Optional.of(leaderMembership));

        assertStatus(() -> service.remove(7L, "member-user", ACTOR_EMAIL), HttpStatus.CONFLICT);

        assertThat(leaderMembership.getStatus()).isEqualTo(ProjectMemberStatus.ACTIVE);
        verify(projectMemberRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService, notificationService);
    }

    @Test
    void listsRemovedMembershipHistoryInRepositoryOrder() {
        ProjectMemberEntity removed = ProjectMemberEntity.createMember(project, member);
        removed.remove();
        when(projectRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findMembersForDisplay(7L, ProjectMemberStatus.REMOVED))
                .thenReturn(List.of(removed));

        List<ProjectMemberResponse> response = service.list(7L, ProjectMemberStatus.REMOVED, ACTOR_EMAIL);

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.getStatus()).isEqualTo(ProjectMemberStatus.REMOVED);
            assertThat(item.getRemovedAt()).isNotNull();
        });
        verify(projectAccessService).requireRead(project, ACTOR_EMAIL);
    }

    @Test
    void searchesOnlyAssignableNonMemberCandidatesWithDatabaseLimit() {
        when(projectRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findAssignableMemberCandidates(
                7L, "nguyen", PageRequest.of(0, 20)
        )).thenReturn(List.of(member));

        var response = service.findCandidates(7L, "  nguyen  ", ACTOR_EMAIL);

        assertThat(response).singleElement().satisfies(candidate ->
                assertThat(candidate.getUserId()).isEqualTo("member-user")
        );
        verify(projectAccessService).requireManage(project, ACTOR_EMAIL);
    }

    private void stubManage() {
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(projectAccessService.requireManage(project, ACTOR_EMAIL)).thenReturn(actor);
    }

    private AddProjectMemberRequest addRequest(String userId) {
        AddProjectMemberRequest request = new AddProjectMemberRequest();
        request.setUserId(userId);
        return request;
    }

    private UserEntity user(Long id, String userId, String email, boolean active) {
        return UserEntity.builder()
                .id(id).userId(userId).name(userId).email(email).password("encoded")
                .isActive(active).isAccountVerified(true).createdAt(Timestamp.from(Instant.now()))
                .build();
    }

    private void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, HttpStatus status) {
        assertThatThrownBy(callable)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(status);
    }
}
