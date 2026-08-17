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
import com.smartlab.repo.ProjectJoinRequestRepository;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.service.AuditService;
import com.smartlab.service.NotificationRelated;
import com.smartlab.service.NotificationService;
import com.smartlab.service.PermissionService;
import com.smartlab.service.ProjectAccessService;
import com.smartlab.service.ProjectJoinRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.smartlab.service.AuditVocabulary.PROJECT_JOIN_REQUEST;
import static com.smartlab.service.AuditVocabulary.PROJECT_JOIN_REQUEST_CREATED;
import static com.smartlab.service.AuditVocabulary.PROJECT_JOIN_REQUEST_UPDATED;
import static com.smartlab.service.AuditVocabulary.PROJECT_MEMBER;
import static com.smartlab.service.AuditVocabulary.PROJECT_MEMBER_CREATED;
import static com.smartlab.service.AuditVocabulary.PROJECT_MEMBER_UPDATED;

@Service
@RequiredArgsConstructor
public class ProjectJoinRequestServiceImpl implements ProjectJoinRequestService {
    private static final int MESSAGE_MAX_LENGTH = 500;

    private final ProjectRepository projectRepository;
    private final ProjectJoinRequestRepository joinRequestRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessService projectAccessService;
    private final PermissionService permissionService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Override
    @Transactional
    public ProjectJoinRequestResponse create(
            Long projectId,
            CreateProjectJoinRequest request,
            String currentEmail
    ) {
        ProjectEntity project = requireProjectForUpdate(projectId);
        UserEntity requester = projectAccessService.requireAuthenticatedUser(currentEmail);
        if (!Boolean.TRUE.equals(project.getIsPublic())) {
            projectAccessService.requireRead(project, currentEmail);
        }
        if (projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                projectId,
                requester.getId(),
                ProjectMemberStatus.ACTIVE
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is already an active project member");
        }
        if (joinRequestRepository.findPendingForRequesterForUpdate(projectId, requester.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A pending join request already exists");
        }

        ProjectJoinRequestEntity joinRequest = ProjectJoinRequestEntity.create(
                project,
                requester,
                normalizeMessage(request == null ? null : request.getMessage())
        );
        ProjectJoinRequestEntity saved;
        try {
            saved = joinRequestRepository.saveAndFlush(joinRequest);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A pending join request already exists");
        }
        auditService.log(
                PROJECT_JOIN_REQUEST_CREATED,
                PROJECT_JOIN_REQUEST,
                saved.getId().toString(),
                null,
                snapshot(saved)
        );
        notifyLeadersOfSubmission(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<ProjectJoinRequestResponse> getLatestMine(Long projectId, String currentEmail) {
        ProjectEntity project = requireProject(projectId);
        UserEntity requester = projectAccessService.requireAuthenticatedUser(currentEmail);
        return joinRequestRepository.findLatestForRequester(
                        project.getId(),
                        requester.getId(),
                        PageRequest.of(0, 1)
                ).stream()
                .findFirst()
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public void cancelMine(Long projectId, String currentEmail) {
        ProjectEntity project = requireProjectForUpdate(projectId);
        UserEntity requester = projectAccessService.requireAuthenticatedUser(currentEmail);
        ProjectJoinRequestEntity joinRequest = joinRequestRepository
                .findPendingForRequesterForUpdate(project.getId(), requester.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Pending join request not found"
                ));
        Map<String, Object> before = snapshot(joinRequest);
        joinRequest.cancel();
        ProjectJoinRequestEntity saved = joinRequestRepository.saveAndFlush(joinRequest);
        auditService.log(
                PROJECT_JOIN_REQUEST_UPDATED,
                PROJECT_JOIN_REQUEST,
                saved.getId().toString(),
                before,
                snapshot(saved)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectJoinRequestResponse> listForManagement(
            Long projectId,
            ProjectJoinRequestStatus status,
            String currentEmail
    ) {
        ProjectEntity project = requireProject(projectId);
        projectAccessService.requireManage(project, currentEmail);
        ProjectJoinRequestStatus requestedStatus = status == null
                ? ProjectJoinRequestStatus.PENDING
                : status;
        return joinRequestRepository.findForManagement(projectId, requestedStatus).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProjectJoinRequestResponse review(
            Long projectId,
            Long requestId,
            ReviewProjectJoinRequest request,
            String currentEmail
    ) {
        ProjectEntity project = requireProjectForUpdate(projectId);
        UserEntity reviewer = projectAccessService.requireManage(project, currentEmail);
        ProjectJoinRequestEntity joinRequest = joinRequestRepository
                .findByProjectAndIdForUpdate(projectId, requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Project join request not found"
                ));
        if (joinRequest.getStatus() != ProjectJoinRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Join request is no longer pending");
        }
        ProjectJoinRequestDecision decision = request == null ? null : request.getDecision();
        if (decision == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Join request decision is required");
        }

        Map<String, Object> before = snapshot(joinRequest);
        Instant now = Instant.now();
        if (decision == ProjectJoinRequestDecision.APPROVE) {
            requireUsableRequester(joinRequest.getRequester());
            approveMembership(project, joinRequest.getRequester());
            joinRequest.approve(reviewer, now);
        } else {
            joinRequest.reject(reviewer, now);
        }
        ProjectJoinRequestEntity saved = joinRequestRepository.saveAndFlush(joinRequest);
        auditService.log(
                PROJECT_JOIN_REQUEST_UPDATED,
                PROJECT_JOIN_REQUEST,
                saved.getId().toString(),
                before,
                snapshot(saved)
        );
        notifyRequesterOfDecision(saved, reviewer);
        return toResponse(saved);
    }

    private void approveMembership(ProjectEntity project, UserEntity requester) {
        ProjectMemberEntity membership = projectMemberRepository
                .findByProject_IdAndUser_Id(project.getId(), requester.getId())
                .orElse(null);
        if (membership != null && membership.getStatus() == ProjectMemberStatus.ACTIVE) {
            return;
        }

        Map<String, Object> before = membership == null ? null : membershipSnapshot(membership);
        if (membership == null) {
            membership = ProjectMemberEntity.createMember(project, requester);
        } else {
            membership.activateAsMember();
        }
        ProjectMemberEntity savedMembership = projectMemberRepository.saveAndFlush(membership);
        auditService.log(
                before == null ? PROJECT_MEMBER_CREATED : PROJECT_MEMBER_UPDATED,
                PROJECT_MEMBER,
                savedMembership.getId().toString(),
                before,
                membershipSnapshot(savedMembership)
        );
    }

    private void requireUsableRequester(UserEntity requester) {
        if (!Boolean.TRUE.equals(requester.getIsActive())
                || permissionService.hasInactiveAssignedRole(requester)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Requester account is no longer active"
            );
        }
    }

    private void notifyLeadersOfSubmission(ProjectJoinRequestEntity joinRequest) {
        ProjectEntity project = joinRequest.getProject();
        UserEntity requester = joinRequest.getRequester();
        projectMemberRepository.findAllByProject_IdAndProjectRoleAndStatus(
                        project.getId(),
                        ProjectRole.LEADER,
                        ProjectMemberStatus.ACTIVE
                ).stream()
                .map(ProjectMemberEntity::getUser)
                .map(UserEntity::getId)
                .distinct()
                .forEach(leaderId -> notificationService.notify(
                        leaderId,
                        "PROJECT_JOIN_REQUESTED",
                        requester.getName() + " requested to join project " + project.getName(),
                        new NotificationRelated(
                                requester.getId(),
                                "PROJECT",
                                project.getId(),
                                "/admin/projects?projectId=" + project.getId()
                        ),
                        Instant.now()
                ));
    }

    private void notifyRequesterOfDecision(ProjectJoinRequestEntity joinRequest, UserEntity reviewer) {
        boolean approved = joinRequest.getStatus() == ProjectJoinRequestStatus.APPROVED;
        ProjectEntity project = joinRequest.getProject();
        notificationService.notify(
                joinRequest.getRequester().getId(),
                approved ? "PROJECT_JOIN_APPROVED" : "PROJECT_JOIN_REJECTED",
                "Your request to join project " + project.getName()
                        + (approved ? " was approved" : " was rejected"),
                new NotificationRelated(
                        reviewer.getId(),
                        "PROJECT",
                        project.getId(),
                        "/du-an/" + project.getId()
                ),
                Instant.now()
        );
    }

    private ProjectEntity requireProject(Long projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> projectNotFound(projectId));
    }

    private ProjectEntity requireProjectForUpdate(Long projectId) {
        return projectRepository.findActiveByIdForUpdate(projectId)
                .orElseThrow(() -> projectNotFound(projectId));
    }

    private ResponseStatusException projectNotFound(Long projectId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId);
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String normalized = message.trim();
        if (normalized.length() > MESSAGE_MAX_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Join request message must not exceed 500 characters"
            );
        }
        return normalized;
    }

    private Map<String, Object> snapshot(ProjectJoinRequestEntity request) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("projectId", request.getProject().getId());
        value.put("requesterUserId", request.getRequester().getId());
        value.put("message", request.getMessage());
        value.put("status", request.getStatus().name());
        value.put("reviewedByUserId", request.getReviewedBy() == null
                ? null
                : request.getReviewedBy().getId());
        value.put("reviewedAt", request.getReviewedAt() == null
                ? null
                : request.getReviewedAt().toString());
        return value;
    }

    private Map<String, Object> membershipSnapshot(ProjectMemberEntity membership) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("projectId", membership.getProject().getId());
        value.put("userId", membership.getUser().getId());
        value.put("projectRole", membership.getProjectRole().name());
        value.put("status", membership.getStatus().name());
        value.put("joinedAt", membership.getJoinedAt() == null
                ? null
                : membership.getJoinedAt().toInstant().toString());
        value.put("removedAt", membership.getRemovedAt() == null
                ? null
                : membership.getRemovedAt().toInstant().toString());
        return value;
    }

    private ProjectJoinRequestResponse toResponse(ProjectJoinRequestEntity request) {
        ProjectEntity project = request.getProject();
        UserEntity requester = request.getRequester();
        UserEntity reviewer = request.getReviewedBy();
        return ProjectJoinRequestResponse.builder()
                .id(request.getId())
                .projectId(project.getId())
                .projectCode(project.getCode())
                .projectName(project.getName())
                .requesterUserId(requester.getUserId())
                .requesterName(requester.getName())
                .requesterEmail(requester.getEmail())
                .message(request.getMessage())
                .status(request.getStatus())
                .reviewedByUserId(reviewer == null ? null : reviewer.getUserId())
                .reviewedByName(reviewer == null ? null : reviewer.getName())
                .reviewedAt(request.getReviewedAt())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }
}
