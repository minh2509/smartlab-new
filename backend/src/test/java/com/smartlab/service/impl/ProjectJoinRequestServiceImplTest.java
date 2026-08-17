package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateProjectJoinRequest;
import com.smartlab.dto.request.ReviewProjectJoinRequest;
import com.smartlab.dto.response.ProjectJoinRequestResponse;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.ProjectJoinRequestEntity;
import com.smartlab.entity.ProjectMemberEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.ProjectJoinRequestDecision;
import com.smartlab.enums.ProjectJoinRequestStatus;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import com.smartlab.repo.ProjectJoinRequestRepository;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.service.AuditService;
import com.smartlab.service.NotificationService;
import com.smartlab.service.PermissionService;
import com.smartlab.service.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectJoinRequestServiceImplTest {
    private static final String REQUESTER_EMAIL = "member@smartlab.test";
    private static final String REVIEWER_EMAIL = "leader@smartlab.test";

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectJoinRequestRepository joinRequestRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private PermissionService permissionService;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;

    @InjectMocks private ProjectJoinRequestServiceImpl service;

    private UserEntity requester;
    private UserEntity reviewer;
    private ProjectEntity publicProject;

    @BeforeEach
    void setUp() {
        requester = user(2L, "member-user", REQUESTER_EMAIL, true);
        reviewer = user(3L, "leader-user", REVIEWER_EMAIL, true);
        publicProject = project(true);
    }

    @Test
    void createsRequestForRemovedMemberAndNotifiesEachLeaderOnce() {
        ProjectMemberEntity removed = ProjectMemberEntity.createMember(publicProject, requester);
        removed.remove();
        ProjectMemberEntity leaderMembership = ProjectMemberEntity.createLeader(publicProject, reviewer);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(publicProject));
        when(projectAccessService.requireAuthenticatedUser(REQUESTER_EMAIL)).thenReturn(requester);
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                7L, 2L, ProjectMemberStatus.ACTIVE
        )).thenReturn(false);
        when(joinRequestRepository.findPendingForRequesterForUpdate(7L, 2L)).thenReturn(Optional.empty());
        when(joinRequestRepository.saveAndFlush(any())).thenAnswer(invocation -> savedRequest(invocation.getArgument(0)));
        when(projectMemberRepository.findAllByProject_IdAndProjectRoleAndStatus(
                7L, ProjectRole.LEADER, ProjectMemberStatus.ACTIVE
        )).thenReturn(List.of(leaderMembership, leaderMembership));
        CreateProjectJoinRequest body = new CreateProjectJoinRequest();
        body.setMessage("  Tôi muốn tham gia  ");

        ProjectJoinRequestResponse response = service.create(7L, body, REQUESTER_EMAIL);

        assertThat(response.getStatus()).isEqualTo(ProjectJoinRequestStatus.PENDING);
        assertThat(response.getMessage()).isEqualTo("Tôi muốn tham gia");
        verify(notificationService).notify(
                eq(3L), eq("PROJECT_JOIN_REQUESTED"), any(String.class), any(), any(Instant.class)
        );
        verify(auditService).log(
                eq("PROJECT_JOIN_REQUEST_CREATED"), eq("PROJECT_JOIN_REQUEST"), eq("41"),
                isNull(), anyMap()
        );
    }

    @Test
    void rejectsPrivateProjectThatRequesterCannotReadWithoutLeakingIt() {
        ProjectEntity privateProject = project(false);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(privateProject));
        when(projectAccessService.requireAuthenticatedUser(REQUESTER_EMAIL)).thenReturn(requester);
        when(projectAccessService.requireRead(privateProject, REQUESTER_EMAIL))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: 7"));

        assertStatus(() -> service.create(7L, null, REQUESTER_EMAIL), HttpStatus.NOT_FOUND);

        verify(joinRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsActiveMemberAndDuplicatePendingRequest() {
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(publicProject));
        when(projectAccessService.requireAuthenticatedUser(REQUESTER_EMAIL)).thenReturn(requester);
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                7L, 2L, ProjectMemberStatus.ACTIVE
        )).thenReturn(true);

        assertStatus(() -> service.create(7L, null, REQUESTER_EMAIL), HttpStatus.CONFLICT);

        verify(joinRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void approvalReactivatesRemovedMembership() {
        ProjectJoinRequestEntity joinRequest = pendingRequest();
        ProjectMemberEntity removedMembership = ProjectMemberEntity.createMember(publicProject, requester);
        ReflectionTestUtils.setField(removedMembership, "id", 90L);
        removedMembership.remove();
        stubReview(joinRequest);
        when(permissionService.hasInactiveAssignedRole(requester)).thenReturn(false);
        when(projectMemberRepository.findByProject_IdAndUser_Id(7L, 2L))
                .thenReturn(Optional.of(removedMembership));
        when(projectMemberRepository.saveAndFlush(removedMembership)).thenReturn(removedMembership);
        when(joinRequestRepository.saveAndFlush(joinRequest)).thenReturn(joinRequest);

        ProjectJoinRequestResponse response = service.review(
                7L,
                41L,
                review(ProjectJoinRequestDecision.APPROVE),
                REVIEWER_EMAIL
        );

        assertThat(response.getStatus()).isEqualTo(ProjectJoinRequestStatus.APPROVED);
        assertThat(removedMembership.getStatus()).isEqualTo(ProjectMemberStatus.ACTIVE);
        assertThat(removedMembership.getProjectRole()).isEqualTo(ProjectRole.MEMBER);
        verify(notificationService).notify(
                eq(2L), eq("PROJECT_JOIN_APPROVED"), any(String.class), any(), any(Instant.class)
        );
    }

    @Test
    void approvalOfStaleRequestNeverDemotesAnActiveLeader() {
        ProjectJoinRequestEntity joinRequest = pendingRequest();
        ProjectMemberEntity activeLeader = ProjectMemberEntity.createLeader(publicProject, requester);
        stubReview(joinRequest);
        when(permissionService.hasInactiveAssignedRole(requester)).thenReturn(false);
        when(projectMemberRepository.findByProject_IdAndUser_Id(7L, 2L))
                .thenReturn(Optional.of(activeLeader));
        when(joinRequestRepository.saveAndFlush(joinRequest)).thenReturn(joinRequest);

        service.review(7L, 41L, review(ProjectJoinRequestDecision.APPROVE), REVIEWER_EMAIL);

        assertThat(activeLeader.getProjectRole()).isEqualTo(ProjectRole.LEADER);
        assertThat(activeLeader.getStatus()).isEqualTo(ProjectMemberStatus.ACTIVE);
        verify(projectMemberRepository, never()).saveAndFlush(any());
    }

    @Test
    void approvalRejectsRequesterWhoseAccountBecameInactive() {
        ProjectJoinRequestEntity joinRequest = pendingRequest();
        requester.setIsActive(false);
        stubReview(joinRequest);

        assertStatus(
                () -> service.review(7L, 41L, review(ProjectJoinRequestDecision.APPROVE), REVIEWER_EMAIL),
                HttpStatus.CONFLICT
        );

        assertThat(joinRequest.getStatus()).isEqualTo(ProjectJoinRequestStatus.PENDING);
        verify(projectMemberRepository, never()).saveAndFlush(any());
        verify(joinRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void crossProjectRequestIdAndTerminalReviewAreRejected() {
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(publicProject));
        when(projectAccessService.requireManage(publicProject, REVIEWER_EMAIL)).thenReturn(reviewer);
        when(joinRequestRepository.findByProjectAndIdForUpdate(7L, 99L)).thenReturn(Optional.empty());

        assertStatus(
                () -> service.review(7L, 99L, review(ProjectJoinRequestDecision.REJECT), REVIEWER_EMAIL),
                HttpStatus.NOT_FOUND
        );

        ProjectJoinRequestEntity rejected = pendingRequest();
        rejected.reject(reviewer, Instant.now());
        when(joinRequestRepository.findByProjectAndIdForUpdate(7L, 41L)).thenReturn(Optional.of(rejected));
        assertStatus(
                () -> service.review(7L, 41L, review(ProjectJoinRequestDecision.REJECT), REVIEWER_EMAIL),
                HttpStatus.CONFLICT
        );
    }

    @Test
    void requesterCanOnlyCancelTheirOwnPendingRequest() {
        ProjectJoinRequestEntity joinRequest = pendingRequest();
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(publicProject));
        when(projectAccessService.requireAuthenticatedUser(REQUESTER_EMAIL)).thenReturn(requester);
        when(joinRequestRepository.findPendingForRequesterForUpdate(7L, 2L))
                .thenReturn(Optional.of(joinRequest));
        when(joinRequestRepository.saveAndFlush(joinRequest)).thenReturn(joinRequest);

        service.cancelMine(7L, REQUESTER_EMAIL);

        assertThat(joinRequest.getStatus()).isEqualTo(ProjectJoinRequestStatus.CANCELLED);
        assertThat(joinRequest.getReviewedAt()).isNull();
        assertThat(joinRequest.getReviewedBy()).isNull();
    }

    private void stubReview(ProjectJoinRequestEntity joinRequest) {
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(publicProject));
        when(projectAccessService.requireManage(publicProject, REVIEWER_EMAIL)).thenReturn(reviewer);
        when(joinRequestRepository.findByProjectAndIdForUpdate(7L, 41L))
                .thenReturn(Optional.of(joinRequest));
    }

    private ProjectJoinRequestEntity pendingRequest() {
        return savedRequest(ProjectJoinRequestEntity.create(publicProject, requester, null));
    }

    private static ProjectJoinRequestEntity savedRequest(ProjectJoinRequestEntity request) {
        ReflectionTestUtils.setField(request, "id", 41L);
        ReflectionTestUtils.setField(request, "createdAt", Instant.parse("2026-08-17T01:00:00Z"));
        ReflectionTestUtils.setField(request, "updatedAt", Instant.parse("2026-08-17T01:00:00Z"));
        return request;
    }

    private static ReviewProjectJoinRequest review(ProjectJoinRequestDecision decision) {
        ReviewProjectJoinRequest request = new ReviewProjectJoinRequest();
        request.setDecision(decision);
        return request;
    }

    private ProjectEntity project(boolean isPublic) {
        ProjectEntity project = ProjectEntity.create(
                "SL-AI", "Smart Lab AI", null, null, ProjectType.RESEARCH, reviewer,
                ProjectStatus.IN_PROGRESS, null, null, null, isPublic, false, reviewer
        );
        ReflectionTestUtils.setField(project, "id", 7L);
        return project;
    }

    private static UserEntity user(Long id, String userId, String email, boolean active) {
        return UserEntity.builder()
                .id(id)
                .userId(userId)
                .name(userId)
                .email(email)
                .isActive(active)
                .build();
    }

    private static void assertStatus(Runnable action, HttpStatus expectedStatus) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(expectedStatus));
    }
}
