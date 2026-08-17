package com.smartlab.service.impl;

import com.smartlab.dto.request.AddProjectMemberRequest;
import com.smartlab.dto.response.ProjectMemberCandidateResponse;
import com.smartlab.dto.response.ProjectMemberResponse;
import com.smartlab.dto.response.ProjectMembershipHistoryResponse;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.ProjectMemberEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.AuditService;
import com.smartlab.service.NotificationRelated;
import com.smartlab.service.NotificationService;
import com.smartlab.service.PermissionService;
import com.smartlab.service.ProjectAccessService;
import com.smartlab.service.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.smartlab.service.AuditVocabulary.PROJECT_MEMBER;
import static com.smartlab.service.AuditVocabulary.PROJECT_MEMBER_CREATED;
import static com.smartlab.service.AuditVocabulary.PROJECT_MEMBER_UPDATED;

@Service
@RequiredArgsConstructor
public class ProjectMemberServiceImpl implements ProjectMemberService {
    private static final int CANDIDATE_LIMIT = 20;
    private static final int QUERY_MAX_LENGTH = 100;
    private static final int PUBLIC_USER_ID_MAX_LENGTH = 36;

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService projectAccessService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final PermissionService permissionService;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> list(
            Long projectId,
            ProjectMemberStatus status,
            String currentEmail
    ) {
        ProjectEntity project = requireProject(projectId);
        projectAccessService.requireRead(project, currentEmail);
        ProjectMemberStatus requestedStatus = status == null ? ProjectMemberStatus.ACTIVE : status;
        return projectMemberRepository.findMembersForDisplay(projectId, requestedStatus).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProjectMemberResponse add(
            Long projectId,
            AddProjectMemberRequest request,
            String currentEmail
    ) {
        ProjectEntity project = requireProjectForUpdate(projectId);
        UserEntity actor = projectAccessService.requireManage(project, currentEmail);
        String memberUserId = normalizeUserId(request == null ? null : request.getUserId());
        UserEntity member = requireActiveMemberAccount(memberUserId);

        ProjectMemberEntity membership = projectMemberRepository
                .findByProject_IdAndUser_Id(projectId, member.getId())
                .orElse(null);
        if (membership != null && membership.getStatus() == ProjectMemberStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is already an active project member");
        }

        Map<String, Object> before = membership == null ? null : snapshot(membership);
        if (membership == null) {
            membership = ProjectMemberEntity.createMember(project, member);
        } else {
            membership.activateAsMember();
        }
        ProjectMemberEntity saved = projectMemberRepository.saveAndFlush(membership);
        auditService.log(
                before == null ? PROJECT_MEMBER_CREATED : PROJECT_MEMBER_UPDATED,
                PROJECT_MEMBER,
                saved.getId().toString(),
                before,
                snapshot(saved)
        );
        notificationService.notify(
                member.getId(),
                "PROJECT_MEMBER_ADDED",
                "You were added to project " + project.getName(),
                new NotificationRelated(actor.getId(), "PROJECT", project.getId(), "/du-an/" + project.getId()),
                Instant.now()
        );
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void remove(Long projectId, String memberUserId, String currentEmail) {
        ProjectEntity project = requireProjectForUpdate(projectId);
        UserEntity actor = projectAccessService.requireManage(project, currentEmail);
        UserEntity member = userRepository.findByUserId(normalizeUserId(memberUserId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member account not found"));
        ProjectMemberEntity membership = projectMemberRepository
                .findByProject_IdAndUser_Id(projectId, member.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project member not found"));

        if (membership.getStatus() == ProjectMemberStatus.REMOVED) {
            return;
        }
        if (membership.getProjectRole() == ProjectRole.LEADER
                || project.getLeader() != null && project.getLeader().getId().equals(member.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A project leader must be demoted before being removed"
            );
        }

        Map<String, Object> before = snapshot(membership);
        membership.remove();
        ProjectMemberEntity saved = projectMemberRepository.saveAndFlush(membership);
        auditService.log(
                PROJECT_MEMBER_UPDATED,
                PROJECT_MEMBER,
                saved.getId().toString(),
                before,
                snapshot(saved)
        );
        notificationService.notify(
                member.getId(),
                "PROJECT_MEMBER_REMOVED",
                "You were removed from project " + project.getName(),
                new NotificationRelated(actor.getId(), "PROJECT", project.getId(), "/du-an/" + project.getId()),
                Instant.now()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMemberCandidateResponse> findCandidates(
            Long projectId,
            String query,
            String currentEmail
    ) {
        ProjectEntity project = requireProject(projectId);
        projectAccessService.requireManage(project, currentEmail);
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() > QUERY_MAX_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Member search query must not exceed 100 characters"
            );
        }
        return projectMemberRepository.findAssignableMemberCandidates(
                        projectId,
                        normalizedQuery,
                        PageRequest.of(0, CANDIDATE_LIMIT)
                ).stream()
                .map(this::toCandidateResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMembershipHistoryResponse> listMine(String currentEmail) {
        UserEntity currentUser = projectAccessService.requireAuthenticatedUser(currentEmail);
        return projectMemberRepository.findMembershipHistoryByUserId(currentUser.getId()).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private ProjectEntity requireProject(Long projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Project not found: " + projectId
                ));
    }

    private ProjectEntity requireProjectForUpdate(Long projectId) {
        return projectRepository.findActiveByIdForUpdate(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Project not found: " + projectId
                ));
    }

    private UserEntity requireActiveMemberAccount(String userId) {
        UserEntity user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member account not found"));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Member account is inactive");
        }
        if (permissionService.hasInactiveAssignedRole(user)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Member account has an inactive role");
        }
        return user;
    }

    private String normalizeUserId(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Member user id is required");
        }
        String normalized = value.trim();
        if (normalized.length() > PUBLIC_USER_ID_MAX_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Member user id must not exceed 36 characters"
            );
        }
        return normalized;
    }

    private Map<String, Object> snapshot(ProjectMemberEntity membership) {
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

    private ProjectMemberResponse toResponse(ProjectMemberEntity membership) {
        UserEntity user = membership.getUser();
        return ProjectMemberResponse.builder()
                .userId(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .projectRole(membership.getProjectRole())
                .status(membership.getStatus())
                .joinedAt(membership.getJoinedAt() == null ? null : membership.getJoinedAt().toInstant())
                .removedAt(membership.getRemovedAt() == null ? null : membership.getRemovedAt().toInstant())
                .build();
    }

    private ProjectMemberCandidateResponse toCandidateResponse(UserEntity user) {
        return ProjectMemberCandidateResponse.builder()
                .userId(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }

    private ProjectMembershipHistoryResponse toHistoryResponse(ProjectMemberEntity membership) {
        ProjectEntity project = membership.getProject();
        return ProjectMembershipHistoryResponse.builder()
                .projectId(project.getId())
                .projectCode(project.getCode())
                .projectName(project.getName())
                .projectStatus(project.getStatus())
                .projectRole(membership.getProjectRole())
                .status(membership.getStatus())
                .joinedAt(membership.getJoinedAt() == null ? null : membership.getJoinedAt().toInstant())
                .removedAt(membership.getRemovedAt() == null ? null : membership.getRemovedAt().toInstant())
                .build();
    }
}
