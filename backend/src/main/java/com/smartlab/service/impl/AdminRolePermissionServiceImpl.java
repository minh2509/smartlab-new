package com.smartlab.service.impl;

import com.smartlab.entity.PermissionEntity;
import com.smartlab.entity.RoleEntity;
import com.smartlab.entity.RolePermissionEntity;
import com.smartlab.dto.request.PermissionRequest;
import com.smartlab.dto.request.RoleRequest;
import com.smartlab.repo.PermissionRepository;
import com.smartlab.repo.RolePermissionRepository;
import com.smartlab.repo.RoleRepository;
import com.smartlab.service.AdminRolePermissionService;
import com.smartlab.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.smartlab.service.AuditVocabulary.*;

@Service
@RequiredArgsConstructor
public class AdminRolePermissionServiceImpl implements AdminRolePermissionService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    @Override
    public List<RoleEntity> getRoles() {
        return roleRepository.findAll();
    }

    @Transactional
    @Override
    public RoleEntity createRole(RoleRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (roleRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role code already exists");
        }
        RoleEntity saved = roleRepository.save(RoleEntity.builder()
                .code(code)
                .name(request.getName())
                .description(request.getDescription())
                .isSystem(false)
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build());
        auditService.log(ROLE_CREATED, ROLE, saved.getId().toString(), null, roleSnapshot(saved));
        return saved;
    }

    @Transactional
    @Override
    public RoleEntity updateRole(String code, RoleRequest request) {
        RoleEntity role = getRole(code);
        Map<String, Object> before = roleSnapshot(role);
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            role.setIsActive(request.getIsActive());
        }
        RoleEntity saved = roleRepository.save(role);
        auditService.log(ROLE_UPDATED, ROLE, saved.getId().toString(), before, roleSnapshot(saved));
        return saved;
    }

    @Transactional
    @Override
    public RoleEntity setRolePermissions(String code, Set<String> permissionCodes) {
        RoleEntity role = getRole(code);
        List<PermissionEntity> permissions = permissionRepository.findByCodeIn(permissionCodes);
        if (permissions.size() != permissionCodes.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more permission codes are invalid");
        }
        List<String> beforeCodes = rolePermissionRepository.findAll().stream()
                .filter(assignment -> role.getId().equals(assignment.getRole().getId()))
                .map(assignment -> assignment.getPermission().getCode())
                .sorted()
                .toList();
        rolePermissionRepository.deleteByRoleId(role.getId());
        for (PermissionEntity permission : permissions) {
            rolePermissionRepository.save(RolePermissionEntity.builder()
                    .role(role)
                    .permission(permission)
                    .build());
        }
        List<String> afterCodes = permissions.stream().map(PermissionEntity::getCode).sorted().toList();
        auditService.log(ROLE_PERMISSIONS_UPDATED, ROLE, role.getId().toString(),
                Map.of("permissionCodes", beforeCodes), Map.of("permissionCodes", afterCodes));
        return role;
    }

    @Transactional(readOnly = true)
    @Override
    public List<PermissionEntity> getPermissions() {
        return permissionRepository.findAll();
    }

    @Transactional
    @Override
    public PermissionEntity createPermission(PermissionRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (permissionRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Permission code already exists");
        }
        PermissionEntity saved = permissionRepository.save(PermissionEntity.builder()
                .code(code)
                .name(request.getName())
                .module(request.getModule())
                .description(request.getDescription())
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build());
        auditService.log(PERMISSION_CREATED, PERMISSION, saved.getId().toString(), null, permissionSnapshot(saved));
        return saved;
    }

    @Transactional
    @Override
    public PermissionEntity updatePermission(String code, PermissionRequest request) {
        PermissionEntity permission = permissionRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission not found"));
        Map<String, Object> before = permissionSnapshot(permission);
        permission.setName(request.getName());
        permission.setModule(request.getModule());
        permission.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            permission.setIsActive(request.getIsActive());
        }
        PermissionEntity saved = permissionRepository.save(permission);
        auditService.log(PERMISSION_UPDATED, PERMISSION, saved.getId().toString(), before, permissionSnapshot(saved));
        return saved;
    }

    private RoleEntity getRole(String code) {
        return roleRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
    }

    private static Map<String, Object> roleSnapshot(RoleEntity role) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", role.getCode());
        snapshot.put("name", role.getName());
        snapshot.put("description", role.getDescription());
        snapshot.put("isSystem", role.getIsSystem());
        snapshot.put("isActive", role.getIsActive());
        return snapshot;
    }

    private static Map<String, Object> permissionSnapshot(PermissionEntity permission) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", permission.getCode());
        snapshot.put("name", permission.getName());
        snapshot.put("module", permission.getModule());
        snapshot.put("description", permission.getDescription());
        snapshot.put("isActive", permission.getIsActive());
        return snapshot;
    }
}
