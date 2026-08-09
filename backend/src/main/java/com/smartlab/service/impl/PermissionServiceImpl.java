package com.smartlab.service.impl;

import com.smartlab.entity.RoleEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.entity.UserPermissionOverrideEntity;
import com.smartlab.enums.PermissionOverrideEffect;
import com.smartlab.repo.RolePermissionRepository;
import com.smartlab.repo.UserPermissionOverrideRepository;
import com.smartlab.repo.UserRoleRepository;
import com.smartlab.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionOverrideRepository userPermissionOverrideRepository;

    @Transactional(readOnly = true)
    @Override
    public Set<String> getRoleCodes(UserEntity user) {
        List<RoleEntity> roles = userRoleRepository.findRolesByUserId(user.getId());
        Set<String> roleCodes = new HashSet<>();
        for (RoleEntity role : roles) {
            roleCodes.add(role.getCode());
        }
        return roleCodes;
    }

    @Transactional(readOnly = true)
    @Override
    public Set<String> getEffectivePermissionCodes(UserEntity user) {
        List<RoleEntity> roles = userRoleRepository.findRolesByUserId(user.getId());
        List<Long> activeRoleIds = roles.stream()
                .filter(role -> Boolean.TRUE.equals(role.getIsActive()))
                .map(RoleEntity::getId)
                .toList();

        Set<String> permissions = activeRoleIds.isEmpty()
                ? new HashSet<>()
                : new HashSet<>(rolePermissionRepository.findActivePermissionCodesByRoleIds(activeRoleIds));

        List<UserPermissionOverrideEntity> overrides = userPermissionOverrideRepository.findByUserId(user.getId());
        for (UserPermissionOverrideEntity override : overrides) {
            if (!Boolean.TRUE.equals(override.getPermission().getIsActive())) {
                continue;
            }
            if (override.getEffect() == PermissionOverrideEffect.GRANT) {
                permissions.add(override.getPermission().getCode());
            } else {
                permissions.remove(override.getPermission().getCode());
            }
        }
        return permissions;
    }

    @Transactional(readOnly = true)
    @Override
    public boolean hasInactiveAssignedRole(UserEntity user) {
        return userRoleRepository.existsByUserIdAndRoleIsActiveFalse(user.getId());
    }
}
