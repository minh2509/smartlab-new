package com.smartlab.service.impl;

import com.smartlab.dto.request.PermissionOverrideRequest;
import com.smartlab.entity.PermissionEntity;
import com.smartlab.entity.RoleEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.entity.UserPermissionOverrideEntity;
import com.smartlab.enums.PermissionOverrideEffect;
import com.smartlab.repo.*;
import com.smartlab.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.smartlab.service.AuditVocabulary.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAccountAuditTest {
    @Mock UserRepository users; @Mock RoleRepository roles; @Mock PermissionRepository permissions;
    @Mock UserRoleRepository userRoles; @Mock UserPermissionOverrideRepository overrides;
    @Mock AccountInvitationRepository invitations; @Mock PermissionService permissionService; @Mock EmailService email;
    @Mock UserSessionService sessions; @Mock TokenHashService hashes; @Mock PasswordEncoder encoder; @Mock AuditService audit;
    AdminAccountServiceImpl service;
    UserEntity user;

    @BeforeEach void setUp() {
        service = new AdminAccountServiceImpl(users, roles, permissions, userRoles, overrides, invitations,
                permissionService, email, sessions, hashes, encoder, audit);
        user = UserEntity.builder().id(11L).userId("external-user-id").email("member@test").name("Member").isActive(true).build();
        when(users.findByUserId("external-user-id")).thenReturn(Optional.of(user));
        when(permissionService.getRoleCodes(user)).thenReturn(Set.of());
        when(permissionService.getEffectivePermissionCodes(user)).thenReturn(Set.of());
    }

    @Test void updateRolesAuditsSortedCodesWithoutSensitiveData() {
        when(userRoles.findRolesByUserId(11L)).thenReturn(List.of(role(2L, "MEMBER"), role(1L, "LEADER")));
        when(roles.findByCodeIn(Set.of("ADMIN", "MEMBER"))).thenReturn(List.of(role(3L, "ADMIN"), role(2L, "MEMBER")));
        service.updateRoles("external-user-id", Set.of("member", "admin"), "admin-external-id");
        verify(audit).log(USER_ROLES_UPDATED, USER, "11",
                Map.of("roleCodes", List.of("LEADER", "MEMBER")), Map.of("roleCodes", List.of("ADMIN", "MEMBER")));
    }

    @Test void setOverrideAuditsPreviousAndResultingEffectOnly() {
        PermissionEntity permission = PermissionEntity.builder().id(5L).code("POST_READ").build();
        UserPermissionOverrideEntity existing = UserPermissionOverrideEntity.builder().id(6L).user(user).permission(permission)
                .effect(PermissionOverrideEffect.DENY).build();
        when(permissions.findByCode("POST_READ")).thenReturn(Optional.of(permission));
        when(overrides.findByUserIdAndPermissionId(11L, 5L)).thenReturn(Optional.of(existing));
        PermissionOverrideRequest request = new PermissionOverrideRequest(); request.setEffect(PermissionOverrideEffect.GRANT);
        service.setPermissionOverride("external-user-id", "POST_READ", request, "admin");
        verify(audit).log(USER_PERMISSION_OVERRIDE_SET, USER, "11",
                Map.of("permissionCode", "POST_READ", "effect", "DENY"),
                Map.of("permissionCode", "POST_READ", "effect", "GRANT"));
    }

    @Test void removeOverrideAuditsOnlyRealMutation() {
        PermissionEntity permission = PermissionEntity.builder().id(5L).code("POST_READ").build();
        UserPermissionOverrideEntity existing = UserPermissionOverrideEntity.builder().id(6L).user(user).permission(permission)
                .effect(PermissionOverrideEffect.GRANT).build();
        when(permissions.findByCode("POST_READ")).thenReturn(Optional.of(permission));
        when(overrides.findByUserIdAndPermissionId(11L, 5L)).thenReturn(Optional.of(existing), Optional.empty());
        service.removePermissionOverride("external-user-id", "POST_READ");
        verify(audit).log(USER_PERMISSION_OVERRIDE_REMOVED, USER, "11",
                Map.of("permissionCode", "POST_READ", "effect", "GRANT"), null);
        service.removePermissionOverride("external-user-id", "POST_READ");
        verify(audit, times(1)).log(any(), any(), any(), any(), any());
    }

    private static RoleEntity role(Long id, String code) { return RoleEntity.builder().id(id).code(code).build(); }
}
