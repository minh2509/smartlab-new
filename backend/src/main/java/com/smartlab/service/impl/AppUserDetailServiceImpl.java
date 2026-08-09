package com.smartlab.service.impl;

import com.smartlab.entity.UserEntity;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AppUserDetailServiceImpl implements AppUserDetailService {
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity existingUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("username not found with email: " + email));
        Set<String> authorities = new HashSet<>(permissionService.getEffectivePermissionCodes(existingUser));
        permissionService.getRoleCodes(existingUser).forEach(roleCode -> authorities.add("ROLE_" + roleCode));
        boolean disabled = !Boolean.TRUE.equals(existingUser.getIsActive())
                || permissionService.hasInactiveAssignedRole(existingUser);

        return User.builder()
                .username(existingUser.getEmail())
                .password(existingUser.getPassword())
                .disabled(disabled)
                .authorities(authorities.toArray(String[]::new))
                .build();
    }
}
