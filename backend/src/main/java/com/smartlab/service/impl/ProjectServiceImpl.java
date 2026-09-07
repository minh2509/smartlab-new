package com.smartlab.service.impl;

import com.smartlab.dto.request.ChangeProjectLeaderRequest;
import com.smartlab.dto.request.ChangeProjectLeadersRequest;
import com.smartlab.dto.request.CreateProjectRequest;
import com.smartlab.dto.request.UpdateProjectRequest;
import com.smartlab.dto.request.UpdateProjectLeadershipRequest;
import com.smartlab.dto.response.LeaderCandidateResponse;
import com.smartlab.dto.response.ProjectLeaderResponse;
import com.smartlab.dto.response.ProjectResearchFieldResponse;
import com.smartlab.dto.response.ProjectResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.dto.response.PublicProjectDetailResponse;
import com.smartlab.dto.response.PublicProjectLeaderResponse;
import com.smartlab.dto.response.PublicProjectSummaryResponse;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.ProjectMemberEntity;
import com.smartlab.entity.ProjectResearchFieldEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import com.smartlab.enums.PublicProjectStatus;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.ProjectResearchFieldRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.AuditService;
import com.smartlab.service.PermissionService;
import com.smartlab.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.smartlab.service.AuditVocabulary.PROJECT_MEMBER;
import static com.smartlab.service.AuditVocabulary.PROJECT_MEMBER_CREATED;
import static com.smartlab.service.AuditVocabulary.PROJECT_MEMBER_UPDATED;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {
    private static final int LEADER_CANDIDATE_LIMIT = 20;
    private static final int PUBLIC_RECRUITING_PAGE_SIZE_MAX = 24;
    private static final int PUBLIC_PROJECT_PAGE_SIZE_MAX = 48;
    private static final int LEADER_SEARCH_QUERY_MAX_LENGTH = 100;
    private static final int PROJECT_LEADER_LIMIT = 100;
    private static final int PUBLIC_USER_ID_MAX_LENGTH = 36;
    private static final List<ProjectStatus> RECRUITABLE_STATUSES = List.of(
            ProjectStatus.PROPOSED,
            ProjectStatus.PREPARING,
            ProjectStatus.IN_PROGRESS
    );
    private static final String ADMIN = "ADMIN";
    private static final String LEADER = "LEADER";
    private static final String PROJECT_READ = "PROJECT_READ";
    private static final String PROJECT_MANAGE = "PROJECT_MANAGE";

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectResearchFieldRepository projectResearchFieldRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> list(String currentEmail) {
        List<ProjectEntity> projects = projectRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc();
        UserEntity currentUser = findCurrentUser(currentEmail);
        if (currentUser == null) {
            return toResponses(projects.stream()
                    .filter(project -> Boolean.TRUE.equals(project.getIsPublic()))
                    .toList());
        }

        Set<String> roles = permissionService.getRoleCodes(currentUser);
        if (roles.contains(ADMIN)) {
            return toResponses(projects);
        }

        boolean canReadInternal = permissionService.getEffectivePermissionCodes(currentUser).contains(PROJECT_READ);
        Set<Long> joinedProjectIds = canReadInternal
                ? new HashSet<>(projectMemberRepository.findActiveProjectIdsByUserId(currentUser.getId()))
                : Set.of();
        return toResponses(projects.stream()
                .filter(project -> Boolean.TRUE.equals(project.getIsPublic())
                        || canReadInternal && joinedProjectIds.contains(project.getId()))
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PublicPageResponse<PublicProjectSummaryResponse> listPublicRecruiting(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must not be negative");
        }
        if (size < 1 || size > PUBLIC_RECRUITING_PAGE_SIZE_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size must be between 1 and 24");
        }
        Page<ProjectEntity> projects = projectRepository.findPublicRecruitingProjects(
                List.of(ProjectStatus.PROPOSED, ProjectStatus.PREPARING, ProjectStatus.IN_PROGRESS),
                PageRequest.of(page, size)
        );
        return toPublicPageResponse(projects);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicPageResponse<PublicProjectSummaryResponse> listPublic(
            int page,
            int size,
            String query,
            Long researchFieldId,
            String researchFieldCode,
            ProjectType projectType,
            PublicProjectStatus status
    ) {
        int pageSize = normalizePageSize(page, size, PUBLIC_PROJECT_PAGE_SIZE_MAX);
        if (researchFieldId != null && researchFieldId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Research field id must be positive");
        }

        List<ProjectStatus> statuses = statusesFor(status);
        Page<ProjectEntity> projects = projectRepository.findPublicProjects(
                normalizeSearch(query),
                projectType,
                statuses,
                status == PublicProjectStatus.RECRUITING,
                status != null && status != PublicProjectStatus.RECRUITING,
                RECRUITABLE_STATUSES,
                researchFieldId,
                normalizeSearch(researchFieldCode),
                PageRequest.of(page, pageSize)
        );
        return toPublicPageResponse(projects);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse get(Long projectId, String currentEmail) {
        ProjectEntity project = getActiveProject(projectId);
        UserEntity currentUser = findCurrentUser(currentEmail);
        if (!canView(project, currentUser)) {
            throw notFound(projectId);
        }
        return toResponse(project, findActiveLeaderMemberships(projectId));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicProjectDetailResponse getPublic(Long projectId) {
        ProjectEntity project = projectRepository.findPublicById(projectId)
                .orElseThrow(() -> notFound(projectId));
        return toPublicDetailResponse(project, findActiveLeaderMemberships(projectId));
    }

    @Override
    @Transactional
    public ProjectResponse create(CreateProjectRequest request, String currentEmail) {
        UserEntity creator = requireProjectCreator(currentEmail);
        boolean adminCreator = permissionService.getRoleCodes(creator).contains(ADMIN);
        String code = normalizeCode(request.getCode());
        if (projectRepository.existsByCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project code already exists");
        }

        String primaryLeaderUserId;
        Map<String, UserEntity> leadersByUserId;
        if (adminCreator) {
            primaryLeaderUserId = normalizeOptionalLeaderUserId(request.getLeaderUserId());
            LinkedHashSet<String> leaderUserIds = normalizeLeaderUserIds(
                    primaryLeaderUserId,
                    request.getAdditionalLeaderUserIds()
            );
            leadersByUserId = lockUsers(leaderUserIds);
            leadersByUserId.values().forEach(this::validateAssignableUser);
        } else {
            validateLeaderSelfAssignmentOnly(request, creator);
            primaryLeaderUserId = creator.getUserId();
            leadersByUserId = Map.of(creator.getUserId(), creator);
        }

        LocalDate startDate = request.getStartDate();
        LocalDate expectedEndDate = request.getExpectedEndDate();
        LocalDate actualEndDate = request.getActualEndDate();
        validateDates(startDate, expectedEndDate, actualEndDate);

        UserEntity primaryLeader = primaryLeaderUserId == null
                ? null
                : leadersByUserId.get(primaryLeaderUserId);
        ProjectEntity project = ProjectEntity.create(
                code,
                normalizeRequiredText(request.getName()),
                normalizeOptional(request.getDescription()),
                normalizeOptional(request.getGoal()),
                request.getProjectType() == null ? ProjectType.RESEARCH : request.getProjectType(),
                primaryLeader,
                request.getStatus() == null ? ProjectStatus.PROPOSED : request.getStatus(),
                startDate,
                expectedEndDate,
                actualEndDate,
                Boolean.TRUE.equals(request.getIsPublic()),
                Boolean.TRUE.equals(request.getIsFeatured()),
                Boolean.TRUE.equals(request.getIsRecruiting()),
                creator
        );

        try {
            ProjectEntity savedProject = projectRepository.saveAndFlush(project);
            List<ProjectMemberEntity> memberships = leadersByUserId.values().stream()
                    .map(leader -> ProjectMemberEntity.createLeader(savedProject, leader))
                    .toList();
            projectMemberRepository.saveAllAndFlush(memberships);
            auditMembershipChanges(memberships.stream()
                    .map(membership -> new MembershipAuditChange(membership, null))
                    .toList());
            return toResponse(savedProject, memberships);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Project code or leader membership already exists"
            );
        }
    }

    @Override
    @Transactional
    public ProjectResponse update(Long projectId, UpdateProjectRequest request, String currentEmail) {
        UserEntity currentUser = requireCurrentUser(currentEmail);
        ProjectEntity project = getActiveProjectForUpdate(projectId);
        requireCanUpdate(project, currentUser);

        String code = request.getCode() == null ? project.getCode() : normalizeCode(request.getCode());
        if (!code.equalsIgnoreCase(project.getCode())
                && projectRepository.existsByCodeIgnoreCaseAndIdNot(code, projectId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project code already exists");
        }

        LocalDate startDate = request.getStartDate() == null ? project.getStartDate() : request.getStartDate();
        LocalDate expectedEndDate = request.getExpectedEndDate() == null
                ? project.getExpectedEndDate()
                : request.getExpectedEndDate();
        LocalDate actualEndDate = request.getActualEndDate() == null
                ? project.getActualEndDate()
                : request.getActualEndDate();
        validateDates(startDate, expectedEndDate, actualEndDate);

        project.updateCore(
                code,
                request.getName() == null ? project.getName() : normalizeRequiredText(request.getName()),
                request.getDescription() == null
                        ? project.getDescription()
                        : normalizeOptional(request.getDescription()),
                request.getGoal() == null ? project.getGoal() : normalizeOptional(request.getGoal()),
                request.getProjectType() == null ? project.getProjectType() : request.getProjectType(),
                request.getStatus() == null ? project.getStatus() : request.getStatus(),
                startDate,
                expectedEndDate,
                actualEndDate,
                request.getIsPublic() == null ? project.getIsPublic() : request.getIsPublic(),
                request.getIsFeatured() == null ? project.getIsFeatured() : request.getIsFeatured(),
                request.getIsRecruiting() == null ? project.getIsRecruiting() : request.getIsRecruiting()
        );

        try {
            ProjectEntity savedProject = projectRepository.saveAndFlush(project);
            return toResponse(savedProject, findActiveLeaderMemberships(projectId));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project code already exists");
        }
    }

    @Override
    @Transactional
    public ProjectResponse changeLeader(
            Long projectId,
            ChangeProjectLeaderRequest request,
            String adminEmail
    ) {
        requireAdmin(adminEmail);
        ProjectEntity project = getActiveProjectForUpdate(projectId);
        UserEntity oldPrimaryLeader = project.getLeader();
        String newPrimaryLeaderUserId = normalizeLeaderUserId(request.getLeaderUserId());

        LinkedHashSet<String> affectedUserIds = new LinkedHashSet<>();
        if (oldPrimaryLeader != null) {
            affectedUserIds.add(oldPrimaryLeader.getUserId());
        }
        affectedUserIds.add(newPrimaryLeaderUserId);
        Map<String, UserEntity> lockedUsers = lockUsers(affectedUserIds);
        UserEntity newPrimaryLeader = lockedUsers.get(newPrimaryLeaderUserId);
        validateAssignableUser(newPrimaryLeader);

        Map<Long, MembershipAuditChange> membershipChanges = new LinkedHashMap<>();
        if (oldPrimaryLeader != null) {
            MembershipAuditChange oldPrimaryMembership = activateLeaderMembership(project, oldPrimaryLeader);
            membershipChanges.put(oldPrimaryLeader.getId(), oldPrimaryMembership);
        }
        if (!membershipChanges.containsKey(newPrimaryLeader.getId())) {
            membershipChanges.put(
                    newPrimaryLeader.getId(),
                    activateLeaderMembership(project, newPrimaryLeader)
            );
        }
        List<ProjectMemberEntity> retainedMemberships = membershipChanges.values().stream()
                .map(MembershipAuditChange::membership)
                .toList();
        projectMemberRepository.saveAllAndFlush(retainedMemberships);
        auditMembershipChanges(membershipChanges.values());

        project.changeLeader(newPrimaryLeader);
        ProjectEntity savedProject = projectRepository.saveAndFlush(project);
        return toResponse(savedProject, findActiveLeaderMemberships(projectId));
    }

    @Override
    @Transactional
    public ProjectResponse replaceLeaders(
            Long projectId,
            ChangeProjectLeadersRequest request,
            String adminEmail
    ) {
        requireAdmin(adminEmail);
        ProjectEntity project = getActiveProjectForUpdate(projectId);
        UserEntity primaryLeader = project.getLeader();
        LinkedHashSet<String> requestedUserIds = normalizeLeaderUserIds(request.getLeaderUserIds());

        List<ProjectMemberEntity> currentLeaderMemberships = projectMemberRepository
                .findAllByProject_IdAndProjectRoleAndStatus(
                        projectId,
                        ProjectRole.LEADER,
                        ProjectMemberStatus.ACTIVE
                );
        LinkedHashSet<String> affectedUserIds = new LinkedHashSet<>(requestedUserIds);
        currentLeaderMemberships.stream()
                .map(ProjectMemberEntity::getUser)
                .map(UserEntity::getUserId)
                .forEach(affectedUserIds::add);
        if (primaryLeader != null) {
            affectedUserIds.add(primaryLeader.getUserId());
        }
        Map<String, UserEntity> lockedUsers = lockUsers(affectedUserIds);
        requestedUserIds.stream()
                .map(lockedUsers::get)
                .forEach(this::validateAssignableUser);

        Map<Long, ProjectMemberEntity> currentMembershipsByUserId = new LinkedHashMap<>();
        currentLeaderMemberships.forEach(membership ->
                currentMembershipsByUserId.put(membership.getUser().getId(), membership));
        List<ProjectMemberEntity> retainedMemberships = new ArrayList<>();
        Map<Long, MembershipAuditChange> membershipChanges = new LinkedHashMap<>();
        for (String requestedUserId : requestedUserIds) {
            UserEntity leader = lockedUsers.get(requestedUserId);
            ProjectMemberEntity membership = currentMembershipsByUserId.get(leader.getId());
            MembershipAuditChange change = membership == null
                    ? activateLeaderMembership(project, leader)
                    : activateAsLeader(membership);
            retainedMemberships.add(change.membership());
            membershipChanges.put(leader.getId(), change);
        }

        List<ProjectMemberEntity> removedMemberships = currentLeaderMemberships.stream()
                .filter(membership -> !requestedUserIds.contains(membership.getUser().getUserId()))
                .toList();
        removedMemberships.forEach(membership -> membershipChanges.put(
                membership.getUser().getId(),
                activateAsMember(membership)
        ));
        List<ProjectMemberEntity> changedMemberships = new ArrayList<>(retainedMemberships);
        changedMemberships.addAll(removedMemberships);
        if (!changedMemberships.isEmpty()) {
            projectMemberRepository.saveAllAndFlush(changedMemberships);
            auditMembershipChanges(membershipChanges.values());
        }

        boolean clearedPrimaryLeader = primaryLeader != null
                && !requestedUserIds.contains(primaryLeader.getUserId());
        if (clearedPrimaryLeader) {
            project.changeLeader(null);
            projectRepository.saveAndFlush(project);
        }

        return toResponse(project, retainedMemberships);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaderCandidateResponse> findLeaderCandidates(String query, String adminEmail) {
        requireAdmin(adminEmail);
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() > LEADER_SEARCH_QUERY_MAX_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Leader search query must not exceed 100 characters"
            );
        }
        return userRepository.findAssignableLeaderCandidates(
                        normalizedQuery,
                        PageRequest.of(0, LEADER_CANDIDATE_LIMIT)
                ).stream()
                .map(this::toLeaderCandidateResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProjectResponse updateLeadership(
            Long projectId,
            UpdateProjectLeadershipRequest request,
            String adminEmail
    ) {
        requireAdmin(adminEmail);
        ProjectEntity project = getActiveProjectForUpdate(projectId);
        String primaryLeaderUserId = normalizeAtomicPrimaryLeaderUserId(request.getPrimaryLeaderUserId());
        LinkedHashSet<String> requestedUserIds = normalizeAtomicLeaderUserIds(request.getLeaderUserIds());
        if (primaryLeaderUserId != null && !requestedUserIds.contains(primaryLeaderUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Primary leader must be included in the leader user ids"
            );
        }

        List<ProjectMemberEntity> currentLeaderMemberships = projectMemberRepository
                .findAllByProject_IdAndProjectRoleAndStatus(
                        projectId,
                        ProjectRole.LEADER,
                        ProjectMemberStatus.ACTIVE
                );
        LinkedHashSet<String> affectedUserIds = new LinkedHashSet<>(requestedUserIds);
        currentLeaderMemberships.stream()
                .map(ProjectMemberEntity::getUser)
                .map(UserEntity::getUserId)
                .forEach(affectedUserIds::add);
        if (project.getLeader() != null) {
            affectedUserIds.add(project.getLeader().getUserId());
        }

        Map<String, UserEntity> lockedUsers = lockUsers(affectedUserIds);
        requestedUserIds.stream()
                .map(lockedUsers::get)
                .forEach(this::validateAssignableUser);

        Map<Long, ProjectMemberEntity> currentMembershipsByUserId = new LinkedHashMap<>();
        currentLeaderMemberships.forEach(membership ->
                currentMembershipsByUserId.put(membership.getUser().getId(), membership));
        List<ProjectMemberEntity> retainedMemberships = new ArrayList<>();
        Map<Long, MembershipAuditChange> membershipChanges = new LinkedHashMap<>();
        for (String requestedUserId : requestedUserIds) {
            UserEntity leader = lockedUsers.get(requestedUserId);
            ProjectMemberEntity membership = currentMembershipsByUserId.get(leader.getId());
            MembershipAuditChange change = membership == null
                    ? activateLeaderMembership(project, leader)
                    : activateAsLeader(membership);
            retainedMemberships.add(change.membership());
            membershipChanges.put(leader.getId(), change);
        }

        List<ProjectMemberEntity> removedMemberships = currentLeaderMemberships.stream()
                .filter(membership -> !requestedUserIds.contains(membership.getUser().getUserId()))
                .toList();
        removedMemberships.forEach(membership -> membershipChanges.put(
                membership.getUser().getId(),
                activateAsMember(membership)
        ));
        List<ProjectMemberEntity> changedMemberships = new ArrayList<>(retainedMemberships);
        changedMemberships.addAll(removedMemberships);
        if (!changedMemberships.isEmpty()) {
            projectMemberRepository.saveAllAndFlush(changedMemberships);
            auditMembershipChanges(membershipChanges.values());
        }

        UserEntity primaryLeader = primaryLeaderUserId == null
                ? null
                : lockedUsers.get(primaryLeaderUserId);
        project.changeLeader(primaryLeader);
        ProjectEntity savedProject = projectRepository.saveAndFlush(project);
        return toResponse(savedProject, retainedMemberships);
    }

    @Override
    @Transactional
    public void delete(Long projectId, String adminEmail) {
        requireAdmin(adminEmail);
        ProjectEntity project = getActiveProjectForUpdate(projectId);
        project.softDelete();
        projectRepository.saveAndFlush(project);
    }

    private UserEntity requireAdmin(String email) {
        UserEntity user = requireCurrentUser(email);
        if (!permissionService.getRoleCodes(user).contains(ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role is required");
        }
        if (!permissionService.getEffectivePermissionCodes(user).contains(PROJECT_MANAGE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project management permission is required");
        }
        return user;
    }

    private UserEntity requireProjectCreator(String email) {
        UserEntity user = requireCurrentUser(email);
        Set<String> roles = permissionService.getRoleCodes(user);
        if (!roles.contains(ADMIN) && !roles.contains(LEADER)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin or leader role is required to create a project"
            );
        }
        if (!permissionService.getEffectivePermissionCodes(user).contains(PROJECT_MANAGE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project management permission is required");
        }
        return user;
    }

    private void validateLeaderSelfAssignmentOnly(CreateProjectRequest request, UserEntity creator) {
        LinkedHashSet<String> requestedLeaderIds = normalizeLeaderUserIds(
                normalizeOptionalLeaderUserId(request.getLeaderUserId()),
                request.getAdditionalLeaderUserIds()
        );
        if (requestedLeaderIds.stream().anyMatch(userId -> !userId.equals(creator.getUserId()))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A leader creating a project may only assign themselves as leader"
            );
        }
    }

    private void requireCanUpdate(ProjectEntity project, UserEntity currentUser) {
        if (permissionService.getRoleCodes(currentUser).contains(ADMIN)) {
            return;
        }
        if (!projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                project.getId(),
                currentUser.getId(),
                ProjectRole.LEADER,
                ProjectMemberStatus.ACTIVE
        )) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only this project's leaders can update it");
        }
    }

    private boolean canView(ProjectEntity project, UserEntity currentUser) {
        if (Boolean.TRUE.equals(project.getIsPublic())) {
            return true;
        }
        if (currentUser == null) {
            return false;
        }
        if (permissionService.getRoleCodes(currentUser).contains(ADMIN)) {
            return true;
        }
        if (!permissionService.getEffectivePermissionCodes(currentUser).contains(PROJECT_READ)) {
            return false;
        }
        return projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                project.getId(),
                currentUser.getId(),
                ProjectMemberStatus.ACTIVE
        );
    }

    private Map<String, UserEntity> lockUsers(Collection<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<UserEntity> lockedUsers = userRepository.findAllByUserIdInForUpdate(userIds);
        Map<String, UserEntity> usersByUserId = new LinkedHashMap<>();
        lockedUsers.forEach(user -> usersByUserId.put(user.getUserId(), user));
        for (String userId : userIds) {
            if (!usersByUserId.containsKey(userId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Leader account not found: " + userId);
            }
        }
        return usersByUserId;
    }

    private void validateAssignableUser(UserEntity user) {
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leader account is inactive");
        }
        if (permissionService.hasInactiveAssignedRole(user)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leader account has an inactive role");
        }
    }

    private MembershipAuditChange activateLeaderMembership(ProjectEntity project, UserEntity leader) {
        return projectMemberRepository.findByProject_IdAndUser_Id(project.getId(), leader.getId())
                .map(this::activateAsLeader)
                .orElseGet(() -> new MembershipAuditChange(ProjectMemberEntity.createLeader(project, leader), null));
    }

    private MembershipAuditChange activateAsLeader(ProjectMemberEntity membership) {
        Map<String, Object> before = membershipSnapshot(membership);
        membership.activateAsLeader();
        return new MembershipAuditChange(membership, before);
    }

    private MembershipAuditChange activateAsMember(ProjectMemberEntity membership) {
        Map<String, Object> before = membershipSnapshot(membership);
        membership.activateAsMember();
        return new MembershipAuditChange(membership, before);
    }

    private void auditMembershipChanges(Collection<MembershipAuditChange> changes) {
        for (MembershipAuditChange change : changes) {
            Map<String, Object> after = membershipSnapshot(change.membership());
            if (change.before() == null) {
                auditService.log(
                        PROJECT_MEMBER_CREATED,
                        PROJECT_MEMBER,
                        change.membership().getId().toString(),
                        null,
                        after
                );
            } else if (!change.before().equals(after)) {
                auditService.log(
                        PROJECT_MEMBER_UPDATED,
                        PROJECT_MEMBER,
                        change.membership().getId().toString(),
                        change.before(),
                        after
                );
            }
        }
    }

    private Map<String, Object> membershipSnapshot(ProjectMemberEntity membership) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("projectId", membership.getProject().getId());
        snapshot.put("userId", membership.getUser().getId());
        snapshot.put("projectRole", membership.getProjectRole().name());
        snapshot.put("status", membership.getStatus().name());
        return snapshot;
    }

    private record MembershipAuditChange(ProjectMemberEntity membership, Map<String, Object> before) {
    }

    private UserEntity requireCurrentUser(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
        if (!isAccountUsable(user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated account is inactive");
        }
        return user;
    }

    private UserEntity findCurrentUser(String email) {
        if (email == null || email.isBlank() || "anonymousUser".equals(email)) {
            return null;
        }
        return userRepository.findByEmail(email)
                .filter(this::isAccountUsable)
                .orElse(null);
    }

    private boolean isAccountUsable(UserEntity user) {
        return Boolean.TRUE.equals(user.getIsActive()) && !permissionService.hasInactiveAssignedRole(user);
    }

    private ProjectEntity getActiveProject(Long projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> notFound(projectId));
    }

    private ProjectEntity getActiveProjectForUpdate(Long projectId) {
        return projectRepository.findActiveByIdForUpdate(projectId)
                .orElseThrow(() -> notFound(projectId));
    }

    private ResponseStatusException notFound(Long projectId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId);
    }

    private void validateDates(
            LocalDate startDate,
            LocalDate expectedEndDate,
            LocalDate actualEndDate
    ) {
        if (startDate != null && expectedEndDate != null && expectedEndDate.isBefore(startDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Expected end date must not be before start date"
            );
        }
        if (startDate != null && actualEndDate != null && actualEndDate.isBefore(startDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Actual end date must not be before start date"
            );
        }
    }

    private LinkedHashSet<String> normalizeLeaderUserIds(String primaryLeaderUserId, List<String> additionalIds) {
        LinkedHashSet<String> userIds = new LinkedHashSet<>();
        if (primaryLeaderUserId != null) {
            userIds.add(normalizeLeaderUserId(primaryLeaderUserId));
        }
        if (additionalIds != null) {
            additionalIds.stream().map(this::normalizeLeaderUserId).forEach(userIds::add);
        }
        validateLeaderLimit(userIds.size());
        return userIds;
    }

    private LinkedHashSet<String> normalizeLeaderUserIds(Collection<String> userIds) {
        if (userIds == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leader list is required");
        }
        validateLeaderLimit(userIds.size());
        LinkedHashSet<String> normalizedUserIds = new LinkedHashSet<>();
        userIds.stream().map(this::normalizeLeaderUserId).forEach(normalizedUserIds::add);
        return normalizedUserIds;
    }

    private LinkedHashSet<String> normalizeAtomicLeaderUserIds(Collection<String> userIds) {
        if (userIds == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leader list is required");
        }
        if (userIds.size() > PROJECT_LEADER_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most 100 project leaders may be selected");
        }
        LinkedHashSet<String> normalizedUserIds = new LinkedHashSet<>();
        for (String userId : userIds) {
            String normalizedUserId = normalizeAtomicLeaderUserId(userId);
            if (!normalizedUserIds.add(normalizedUserId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Leader user ids must not contain duplicates"
                );
            }
        }
        return normalizedUserIds;
    }

    private String normalizeAtomicPrimaryLeaderUserId(String value) {
        return value == null ? null : normalizeAtomicLeaderUserId(value);
    }

    private String normalizeAtomicLeaderUserId(String value) {
        return normalizeLeaderUserId(value);
    }

    private String normalizeOptionalLeaderUserId(String value) {
        return value == null ? null : normalizeLeaderUserId(value);
    }

    private String normalizeLeaderUserId(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leader user id is required");
        }
        String normalizedUserId = value.trim();
        if (normalizedUserId.length() > PUBLIC_USER_ID_MAX_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Leader user id must not exceed 36 characters"
            );
        }
        return normalizedUserId;
    }

    private void validateLeaderLimit(int leaderCount) {
        if (leaderCount > PROJECT_LEADER_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most 100 project leaders may be selected");
        }
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase();
    }

    private String normalizeRequiredText(String value) {
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeSearch(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? "" : normalized;
    }

    private List<ProjectResponse> toResponses(List<ProjectEntity> projects) {
        if (projects.isEmpty()) {
            return List.of();
        }
        List<Long> projectIds = projects.stream().map(ProjectEntity::getId).toList();
        Map<Long, List<ProjectMemberEntity>> membershipsByProjectId = projectMemberRepository
                .findActiveLeadersByProjectIds(projectIds)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        membership -> membership.getProject().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        Map<Long, List<ProjectResearchFieldEntity>> fieldsByProjectId = projectResearchFieldRepository
                .findAllWithFieldByProjectIds(projectIds)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        field -> field.getProject().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        return projects.stream()
                .map(project -> toResponse(
                        project,
                        membershipsByProjectId.getOrDefault(project.getId(), List.of()),
                        fieldsByProjectId.getOrDefault(project.getId(), List.of())
                ))
                .toList();
    }

    private PublicPageResponse<PublicProjectSummaryResponse> toPublicPageResponse(Page<ProjectEntity> projects) {
        List<ProjectEntity> projectContent = projects.getContent();
        if (projectContent.isEmpty()) {
            return PublicPageResponse.from(projects.map(project -> toPublicSummaryResponse(project, List.of(), List.of())));
        }

        List<Long> projectIds = projectContent.stream().map(ProjectEntity::getId).toList();
        Map<Long, List<ProjectMemberEntity>> membershipsByProjectId = projectMemberRepository
                .findActiveLeadersByProjectIds(projectIds)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        membership -> membership.getProject().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        Map<Long, List<ProjectResearchFieldEntity>> fieldsByProjectId = projectResearchFieldRepository
                .findAllWithFieldByProjectIds(projectIds)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        field -> field.getProject().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));

        List<PublicProjectSummaryResponse> responses = projectContent.stream()
                .map(project -> toPublicSummaryResponse(
                        project,
                        membershipsByProjectId.getOrDefault(project.getId(), List.of()),
                        fieldsByProjectId.getOrDefault(project.getId(), List.of())
                ))
                .toList();
        return new PublicPageResponse<>(
                responses,
                projects.getNumber(),
                projects.getSize(),
                projects.getTotalElements(),
                projects.getTotalPages()
        );
    }

    private PublicProjectSummaryResponse toPublicSummaryResponse(
            ProjectEntity project,
            Collection<ProjectMemberEntity> leaderMemberships,
            Collection<ProjectResearchFieldEntity> researchFields
    ) {
        UserEntity primaryLeader = project.getLeader();
        Map<String, UserEntity> leadersByUserId = new LinkedHashMap<>();
        if (primaryLeader != null) {
            leadersByUserId.put(primaryLeader.getUserId(), primaryLeader);
        }
        leaderMemberships.stream()
                .map(ProjectMemberEntity::getUser)
                .sorted(Comparator.comparing(UserEntity::getId))
                .forEach(leader -> leadersByUserId.putIfAbsent(leader.getUserId(), leader));

        return new PublicProjectSummaryResponse(
                project.getId(),
                project.getCode(),
                project.getName(),
                project.getDescription(),
                project.getGoal(),
                project.getProjectType(),
                toPublicProjectStatus(project.getStatus(), project.getIsRecruiting()),
                researchFields.stream().map(this::toResearchFieldResponse).toList(),
                leadersByUserId.values().stream().map(this::toPublicLeaderResponse).toList()
        );
    }

    private List<ProjectMemberEntity> findActiveLeaderMemberships(Long projectId) {
        return projectMemberRepository.findActiveLeadersByProjectIds(List.of(projectId));
    }

    private ProjectResponse toResponse(
            ProjectEntity project,
            Collection<ProjectMemberEntity> leaderMemberships
    ) {
        List<ProjectResearchFieldEntity> researchFields = project.getId() == null
                ? List.of()
                : projectResearchFieldRepository.findAllWithFieldByProjectId(project.getId());
        return toResponse(project, leaderMemberships, researchFields);
    }

    private ProjectResponse toResponse(
            ProjectEntity project,
            Collection<ProjectMemberEntity> leaderMemberships,
            Collection<ProjectResearchFieldEntity> researchFields
    ) {
        UserEntity primaryLeader = project.getLeader();
        Map<String, UserEntity> leadersByUserId = new LinkedHashMap<>();
        if (primaryLeader != null) {
            leadersByUserId.put(primaryLeader.getUserId(), primaryLeader);
        }
        leaderMemberships.stream()
                .map(ProjectMemberEntity::getUser)
                .sorted(Comparator.comparing(UserEntity::getId))
                .forEach(leader -> leadersByUserId.putIfAbsent(leader.getUserId(), leader));

        return ProjectResponse.builder()
                .id(project.getId())
                .code(project.getCode())
                .name(project.getName())
                .description(project.getDescription())
                .goal(project.getGoal())
                .projectType(project.getProjectType())
                .status(project.getStatus())
                .publicStatus(toPublicProjectStatus(project.getStatus(), project.getIsRecruiting()))
                .startDate(project.getStartDate())
                .expectedEndDate(project.getExpectedEndDate())
                .actualEndDate(project.getActualEndDate())
                .isPublic(project.getIsPublic())
                .isFeatured(project.getIsFeatured())
                .isRecruiting(project.getIsRecruiting())
                .researchFields(researchFields.stream().map(this::toResearchFieldResponse).toList())
                .primaryLeader(primaryLeader == null ? null : toLeaderResponse(primaryLeader))
                .leaders(leadersByUserId.values().stream().map(this::toLeaderResponse).toList())
                .createdAt(toInstant(project.getCreatedAt()))
                .updatedAt(toInstant(project.getUpdatedAt()))
                .build();
    }

    private PublicProjectDetailResponse toPublicDetailResponse(
            ProjectEntity project,
            Collection<ProjectMemberEntity> leaderMemberships
    ) {
        List<ProjectResearchFieldEntity> researchFields = projectResearchFieldRepository
                .findAllWithFieldByProjectId(project.getId());
        UserEntity primaryLeader = project.getLeader();
        Map<String, UserEntity> leadersByUserId = new LinkedHashMap<>();
        if (primaryLeader != null) {
            leadersByUserId.put(primaryLeader.getUserId(), primaryLeader);
        }
        leaderMemberships.stream()
                .map(ProjectMemberEntity::getUser)
                .sorted(Comparator.comparing(UserEntity::getId))
                .forEach(leader -> leadersByUserId.putIfAbsent(leader.getUserId(), leader));

        return new PublicProjectDetailResponse(
                project.getId(),
                project.getCode(),
                project.getName(),
                project.getDescription(),
                project.getGoal(),
                project.getProjectType(),
                toPublicProjectStatus(project.getStatus(), project.getIsRecruiting()),
                project.getStartDate(),
                project.getExpectedEndDate(),
                project.getActualEndDate(),
                project.getIsFeatured(),
                researchFields.stream().map(this::toResearchFieldResponse).toList(),
                primaryLeader == null ? null : toPublicLeaderResponse(primaryLeader),
                leadersByUserId.values().stream().map(this::toPublicLeaderResponse).toList()
        );
    }

    private ProjectResearchFieldResponse toResearchFieldResponse(ProjectResearchFieldEntity field) {
        return ProjectResearchFieldResponse.builder()
                .id(field.getResearchField().getId())
                .code(field.getResearchField().getCode())
                .name(field.getResearchField().getName())
                .isActive(field.getResearchField().getIsActive())
                .build();
    }

    private List<ProjectStatus> statusesFor(PublicProjectStatus status) {
        if (status == null) {
            return List.of(ProjectStatus.values());
        }
        return switch (status) {
            case RECRUITING -> RECRUITABLE_STATUSES;
            case UPCOMING -> List.of(ProjectStatus.PROPOSED, ProjectStatus.PREPARING);
            case ACTIVE -> List.of(ProjectStatus.IN_PROGRESS, ProjectStatus.PAUSED);
            case COMPLETED -> List.of(ProjectStatus.COMPLETED, ProjectStatus.CLOSED);
        };
    }

    private PublicProjectStatus toPublicProjectStatus(ProjectStatus status, Boolean isRecruiting) {
        if (status == null) {
            return null;
        }
        if (isEffectiveRecruiting(status, isRecruiting)) {
            return PublicProjectStatus.RECRUITING;
        }
        return switch (status) {
            case PROPOSED, PREPARING -> PublicProjectStatus.UPCOMING;
            case IN_PROGRESS, PAUSED -> PublicProjectStatus.ACTIVE;
            case COMPLETED, CLOSED -> PublicProjectStatus.COMPLETED;
        };
    }

    private boolean isEffectiveRecruiting(ProjectStatus status, Boolean isRecruiting) {
        return Boolean.TRUE.equals(isRecruiting) && RECRUITABLE_STATUSES.contains(status);
    }

    private int normalizePageSize(int page, int size, int maxSize) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must not be negative");
        }
        if (size < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size must be at least 1");
        }
        return Math.min(size, maxSize);
    }

    private ProjectLeaderResponse toLeaderResponse(UserEntity leader) {
        return ProjectLeaderResponse.builder()
                .userId(leader.getUserId())
                .name(leader.getName())
                .build();
    }

    private PublicProjectLeaderResponse toPublicLeaderResponse(UserEntity leader) {
        return new PublicProjectLeaderResponse(leader.getName());
    }

    private LeaderCandidateResponse toLeaderCandidateResponse(UserEntity candidate) {
        return LeaderCandidateResponse.builder()
                .userId(candidate.getUserId())
                .name(candidate.getName())
                .email(candidate.getEmail())
                .build();
    }

    private java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
