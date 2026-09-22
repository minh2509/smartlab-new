package com.smartlab.service.impl;

import com.smartlab.dto.request.PermissionOverrideRequest;
import com.smartlab.dto.request.AccountProvisionRequest;
import com.smartlab.dto.request.AccountUpdateRequest;
import com.smartlab.entity.AccountInvitationEntity;
import com.smartlab.entity.MemberProfileEntity;
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
import java.util.concurrent.atomic.AtomicReference;

import static com.smartlab.service.AuditVocabulary.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAccountAuditTest {
    @Mock UserRepository users; @Mock RoleRepository roles; @Mock PermissionRepository permissions;
    @Mock UserRoleRepository userRoles; @Mock UserPermissionOverrideRepository overrides;
    @Mock AccountInvitationRepository invitations; @Mock MemberProfileRepository memberProfiles;
    @Mock PermissionService permissionService; @Mock EmailOutboxRepository emailOutbox;
    @Mock AccountInvitationOutboxStateService outboxState;
    @Mock UserSessionService sessions; @Mock TokenHashService hashes; @Mock PasswordEncoder encoder; @Mock AuditService audit;
    AdminAccountServiceImpl service;
    UserEntity user;

    @BeforeEach void setUp() {
        service = new AdminAccountServiceImpl(users, roles, permissions, userRoles, overrides, invitations, memberProfiles,
                permissionService, emailOutbox, outboxState, sessions, hashes, encoder, audit);
        user = UserEntity.builder().id(11L).userId("external-user-id").email("member@test").name("Member")
                .isActive(true).isAccountVerified(true).build();
        lenient().when(users.findByUserId("external-user-id")).thenReturn(Optional.of(user));
        lenient().when(permissionService.getRoleCodes(user)).thenReturn(Set.of());
        lenient().when(permissionService.getEffectivePermissionCodes(user)).thenReturn(Set.of());
    }

    @Test void updateRolesAuditsSortedCodesWithoutSensitiveData() {
        when(userRoles.findRolesByUserId(11L)).thenReturn(List.of(role(2L, "MEMBER"), role(1L, "LEADER")));
        when(roles.findByCodeIn(Set.of("ADMIN", "MEMBER"))).thenReturn(List.of(role(3L, "ADMIN"), role(2L, "MEMBER")));
        service.updateRoles("external-user-id", Set.of("member", "admin"), "admin-external-id");
        verify(audit).log(USER_ROLES_UPDATED, USER, "11",
                Map.of("roleCodes", List.of("LEADER", "MEMBER")), Map.of("roleCodes", List.of("ADMIN", "MEMBER")));
    }

    @Test void deactivateAccountMarksInactiveAndRevokesSessions() {
        MemberProfileEntity profile = MemberProfileEntity.create(user);
        when(memberProfiles.findByUserId(11L)).thenReturn(Optional.of(profile));

        var response = service.setActive("external-user-id", false);

        assertThat(user.getIsActive()).isFalse();
        assertThat(profile.getActiveStatus()).isEqualTo("INACTIVE");
        assertThat(response.getIsActive()).isFalse();
        verify(users).save(user);
        verify(memberProfiles).save(profile);
        verify(sessions).revokeAllByEmail("member@test");
        verify(audit).log(USER_ACTIVE_STATUS_UPDATED, USER, "11",
                Map.of("isActive", true, "activeStatus", "ACTIVE"),
                Map.of("isActive", false, "activeStatus", "INACTIVE"));
        verify(users, never()).delete(any(UserEntity.class));
    }

    @Test void updateInformationNormalizesNameAndAuditsOnlyEditableData() {
        AccountUpdateRequest request = new AccountUpdateRequest();
        request.setName("  Nguyen   Van   Member  ");

        var response = service.updateInformation("external-user-id", request);

        assertThat(response.getName()).isEqualTo("Nguyen Van Member");
        verify(users).save(user);
        verify(audit).log(USER_INFORMATION_UPDATED, USER, "11",
                Map.of("name", "Member"), Map.of("name", "Nguyen Van Member"));
    }

    @Test void updateInformationDoesNotAuditUnchangedNormalizedName() {
        AccountUpdateRequest request = new AccountUpdateRequest();
        request.setName(" Member ");

        service.updateInformation("external-user-id", request);

        verify(users, never()).save(any(UserEntity.class));
        verifyNoInteractions(audit);
    }

    @Test void settingSameAccountStatusDoesNotCreateFalseAuditMutation() {
        MemberProfileEntity profile = MemberProfileEntity.create(user);
        when(memberProfiles.findByUserId(11L)).thenReturn(Optional.of(profile));

        service.setActive("external-user-id", true);

        verifyNoInteractions(audit);
    }

    @Test void provisionAuditsCreatedAccountWithRolesAndWithoutCredentials() {
        AccountProvisionRequest request = new AccountProvisionRequest();
        request.setName("New Member");
        request.setEmail("new-member@test");
        request.setRoleCodes(Set.of("member", "leader"));
        RoleEntity member = role(2L, "MEMBER");
        RoleEntity leader = role(3L, "LEADER");
        AccountInvitationEntity invitation = AccountInvitationEntity.builder()
                .email("new-member@test")
                .status(com.smartlab.enums.InvitationStatus.PENDING)
                .expiresAt(java.time.Instant.now().plusSeconds(3600))
                .build();
        AtomicReference<UserEntity> savedUser = new AtomicReference<>();
        when(users.existsByEmail("new-member@test")).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("encoded-temporary-password");
        when(users.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            saved.setId(21L);
            savedUser.set(saved);
            return saved;
        });
        when(roles.findByCodeIn(Set.of("MEMBER", "LEADER"))).thenReturn(List.of(member, leader));
        when(invitations.findByEmail("new-member@test"))
                .thenReturn(Optional.empty(), Optional.of(invitation));
        when(hashes.sha256(anyString())).thenReturn("hashed-token");

        service.provision(request, "admin@test");

        verify(audit).log(eq(USER_PROVISIONED), eq(USER), eq("21"), isNull(), argThat(after ->
                after.get("userId").equals(savedUser.get().getUserId())
                        && after.get("name").equals("New Member")
                        && after.get("email").equals("new-member@test")
                        && after.get("isActive").equals(false)
                        && after.get("isAccountVerified").equals(false)
                        && after.get("roleCodes").equals(List.of("LEADER", "MEMBER"))
                        && !after.containsKey("password")
                        && !after.containsKey("token")));
    }

    @Test void setOverrideAuditsPreviousAndResultingEffectOnly() {
        PermissionEntity permission = PermissionEntity.builder().id(5L).code("POST_READ").isActive(true).build();
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

    @Test void accountResponseExposesSortedOverridesSeparatelyFromEffectivePermissions() {
        PermissionEntity write = PermissionEntity.builder().id(7L).code("WRITE").isActive(true).build();
        PermissionEntity read = PermissionEntity.builder().id(5L).code("READ").isActive(true).build();
        when(overrides.findByUserId(11L)).thenReturn(List.of(
                UserPermissionOverrideEntity.builder().user(user).permission(write).effect(PermissionOverrideEffect.DENY).build(),
                UserPermissionOverrideEntity.builder().user(user).permission(read).effect(PermissionOverrideEffect.GRANT).build()));

        var response = service.toResponse(user);

        assertThat(response.getPermissionOverrides())
                .extracting("permissionCode", "effect")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("READ", PermissionOverrideEffect.GRANT),
                        org.assertj.core.groups.Tuple.tuple("WRITE", PermissionOverrideEffect.DENY));
    }

    @Test void inactivePermissionCannotBeOverridden() {
        PermissionEntity permission = PermissionEntity.builder().id(5L).code("POST_READ").isActive(false).build();
        when(permissions.findByCode("POST_READ")).thenReturn(Optional.of(permission));
        PermissionOverrideRequest request = new PermissionOverrideRequest();
        request.setEffect(PermissionOverrideEffect.GRANT);

        assertThatThrownBy(() -> service.setPermissionOverride(
                "external-user-id", " post_read ", request, "admin"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Inactive permissions");

        verify(overrides, never()).save(any());
        verifyNoInteractions(sessions, audit);
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

    private static RoleEntity role(Long id, String code) { return RoleEntity.builder().id(id).code(code).isActive(true).build(); }
}
