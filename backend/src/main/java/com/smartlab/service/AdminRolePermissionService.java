package com.smartlab.service;

import com.smartlab.dto.request.PermissionRequest;
import com.smartlab.dto.request.RoleRequest;
import com.smartlab.entity.PermissionEntity;
import com.smartlab.entity.RoleEntity;

import java.util.List;
import java.util.Set;

public interface AdminRolePermissionService {
    List<RoleEntity> getRoles();

    RoleEntity createRole(RoleRequest request);

    RoleEntity updateRole(String code, RoleRequest request);

    RoleEntity setRolePermissions(String code, Set<String> permissionCodes);

    List<PermissionEntity> getPermissions();

    PermissionEntity createPermission(PermissionRequest request);

    PermissionEntity updatePermission(String code, PermissionRequest request);
}
