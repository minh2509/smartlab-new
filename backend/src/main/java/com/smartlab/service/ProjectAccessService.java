package com.smartlab.service;

import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectAccessService {
    private static final String ADMIN = "ADMIN";
    private static final String PROJECT_READ = "PROJECT_READ";
    private static final String PROJECT_MANAGE = "PROJECT_MANAGE";

    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PermissionService permissionService;

    public UserEntity requireRead(ProjectEntity project, String authenticatedEmail) {
        UserEntity user = requireUsableUser(authenticatedEmail);
        boolean memberReader = permissionService.getEffectivePermissionCodes(user).contains(PROJECT_READ)
                && isActiveMember(project.getId(), user.getId());
        if (isAdmin(user) || memberReader) {
            return user;
        }
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Project not found: " + project.getId()
        );
    }

    public UserEntity requireManage(ProjectEntity project, String authenticatedEmail) {
        UserEntity user = requireUsableUser(authenticatedEmail);
        Set<String> roles = permissionService.getRoleCodes(user);
        Set<String> permissions = permissionService.getEffectivePermissionCodes(user);
        boolean adminManager = roles.contains(ADMIN) && permissions.contains(PROJECT_MANAGE);
        boolean activeLeader = projectMemberRepository
                .existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                        project.getId(),
                        user.getId(),
                        ProjectRole.LEADER,
                        ProjectMemberStatus.ACTIVE
                );
        if (adminManager || activeLeader) {
            return user;
        }
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Project management permission or active project leadership is required"
        );
    }

    private UserEntity requireUsableUser(String email) {
        if (email == null || email.isBlank() || "anonymousUser".equals(email)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));
        if (!Boolean.TRUE.equals(user.getIsActive()) || permissionService.hasInactiveAssignedRole(user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated account is inactive");
        }
        return user;
    }

    private boolean isAdmin(UserEntity user) {
        return permissionService.getRoleCodes(user).contains(ADMIN);
    }

    private boolean isActiveMember(Long projectId, Long userId) {
        return projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                projectId,
                userId,
                ProjectMemberStatus.ACTIVE
        );
    }
}
