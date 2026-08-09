package com.smartlab.service;

import com.smartlab.entity.UserEntity;

import java.util.Set;

public interface PermissionService {
    Set<String> getRoleCodes(UserEntity user);

    Set<String> getEffectivePermissionCodes(UserEntity user);

    boolean hasInactiveAssignedRole(UserEntity user);
}
