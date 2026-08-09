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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminRolePermissionServiceImpl implements AdminRolePermissionService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

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
        return roleRepository.save(RoleEntity.builder()
                .code(code)
                .name(request.getName())
                .description(request.getDescription())
                .isSystem(false)
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build());
    }

    @Transactional
    @Override
    public RoleEntity updateRole(String code, RoleRequest request) {
        RoleEntity role = getRole(code);
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            role.setIsActive(request.getIsActive());
        }
        return roleRepository.save(role);
    }

    @Transactional
    @Override
    public RoleEntity setRolePermissions(String code, Set<String> permissionCodes) {
        RoleEntity role = getRole(code);
        List<PermissionEntity> permissions = permissionRepository.findByCodeIn(permissionCodes);
        if (permissions.size() != permissionCodes.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more permission codes are invalid");
        }
        rolePermissionRepository.deleteByRoleId(role.getId());
        for (PermissionEntity permission : permissions) {
            rolePermissionRepository.save(RolePermissionEntity.builder()
                    .role(role)
                    .permission(permission)
                    .build());
        }
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
        return permissionRepository.save(PermissionEntity.builder()
                .code(code)
                .name(request.getName())
                .module(request.getModule())
                .description(request.getDescription())
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build());
    }

    @Transactional
    @Override
    public PermissionEntity updatePermission(String code, PermissionRequest request) {
        PermissionEntity permission = permissionRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission not found"));
        permission.setName(request.getName());
        permission.setModule(request.getModule());
        permission.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            permission.setIsActive(request.getIsActive());
        }
        return permissionRepository.save(permission);
    }

    private RoleEntity getRole(String code) {
        return roleRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
    }
}
