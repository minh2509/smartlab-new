package com.smartlab.service.impl;

import com.smartlab.dto.request.PermissionRequest;
import com.smartlab.dto.request.RoleRequest;
import com.smartlab.entity.PermissionEntity;
import com.smartlab.entity.RoleEntity;
import com.smartlab.entity.RolePermissionEntity;
import com.smartlab.repo.PermissionRepository;
import com.smartlab.repo.RolePermissionRepository;
import com.smartlab.repo.RoleRepository;
import com.smartlab.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.smartlab.service.AuditVocabulary.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminRolePermissionAuditTest {
    @Mock RoleRepository roles;
    @Mock PermissionRepository permissions;
    @Mock RolePermissionRepository rolePermissions;
    @Mock AuditService audit;
    AdminRolePermissionServiceImpl service;

    @BeforeEach void setUp() { service = new AdminRolePermissionServiceImpl(roles, permissions, rolePermissions, audit); }

    @Test void createAndUpdateRoleAuditIntentionalSnapshots() {
        RoleRequest create = role(" analyst ", "Analyst", "new", true);
        when(roles.save(any())).thenAnswer(inv -> { RoleEntity role = inv.getArgument(0); role.setId(7L); return role; });
        service.createRole(create);
        verify(audit).log(ROLE_CREATED, ROLE, "7", null,
                Map.of("code", "ANALYST", "name", "Analyst", "description", "new", "isSystem", false, "isActive", true));

        RoleEntity existing = RoleEntity.builder().id(7L).code("ANALYST").name("Old").description("old")
                .isSystem(false).isActive(true).build();
        when(roles.findByCode("ANALYST")).thenReturn(Optional.of(existing));
        when(roles.save(existing)).thenReturn(existing);
        service.updateRole("analyst", role("ignored", "New", "changed", false));
        verify(audit).log(ROLE_UPDATED, ROLE, "7",
                Map.of("code", "ANALYST", "name", "Old", "description", "old", "isSystem", false, "isActive", true),
                Map.of("code", "ANALYST", "name", "New", "description", "changed", "isSystem", false, "isActive", false));
    }

    @Test void setPermissionsAuditsSortedBeforeAndAfterOnlyAfterValidation() {
        RoleEntity role = RoleEntity.builder().id(7L).code("ANALYST").build();
        PermissionEntity oldB = permission(2L, "B"), oldA = permission(1L, "A"), nextC = permission(3L, "C");
        when(roles.findByCode("ANALYST")).thenReturn(Optional.of(role));
        when(rolePermissions.findAll()).thenReturn(List.of(
                RolePermissionEntity.builder().role(role).permission(oldB).build(),
                RolePermissionEntity.builder().role(role).permission(oldA).build()));
        when(permissions.findByCodeIn(Set.of("C", "A"))).thenReturn(List.of(nextC, oldA));
        service.setRolePermissions("analyst", Set.of("C", "A"));
        verify(audit).log(ROLE_PERMISSIONS_UPDATED, ROLE, "7",
                Map.of("permissionCodes", List.of("A", "B")), Map.of("permissionCodes", List.of("A", "C")));
    }

    @Test void createAndUpdatePermissionAuditSnapshotsAndValidationFailureDoesNotAudit() {
        when(permissions.save(any())).thenAnswer(inv -> { PermissionEntity p = inv.getArgument(0); p.setId(8L); return p; });
        service.createPermission(permissionRequest("read", "Read", "POST", "create", true));
        verify(audit).log(PERMISSION_CREATED, PERMISSION, "8", null,
                Map.of("code", "READ", "name", "Read", "module", "POST", "description", "create", "isActive", true));

        PermissionEntity existing = PermissionEntity.builder().id(8L).code("READ").name("Old").module("OLD")
                .description("before").isActive(true).build();
        when(permissions.findByCode("READ")).thenReturn(Optional.of(existing));
        when(permissions.save(existing)).thenReturn(existing);
        service.updatePermission("read", permissionRequest("ignored", "New", "POST", "after", false));
        verify(audit).log(PERMISSION_UPDATED, PERMISSION, "8",
                Map.of("code", "READ", "name", "Old", "module", "OLD", "description", "before", "isActive", true),
                Map.of("code", "READ", "name", "New", "module", "POST", "description", "after", "isActive", false));

        when(roles.existsByCode("DUP")).thenReturn(true);
        assertThatThrownBy(() -> service.createRole(role("dup", "Dup", null, true))).isInstanceOf(ResponseStatusException.class);
        verifyNoMoreInteractions(audit);
    }

    private static RoleRequest role(String code, String name, String description, boolean active) { RoleRequest r = new RoleRequest(); r.setCode(code); r.setName(name); r.setDescription(description); r.setIsActive(active); return r; }
    private static PermissionRequest permissionRequest(String code, String name, String module, String description, boolean active) { PermissionRequest r = new PermissionRequest(); r.setCode(code); r.setName(name); r.setModule(module); r.setDescription(description); r.setIsActive(active); return r; }
    private static PermissionEntity permission(Long id, String code) { return PermissionEntity.builder().id(id).code(code).isActive(true).build(); }
}
