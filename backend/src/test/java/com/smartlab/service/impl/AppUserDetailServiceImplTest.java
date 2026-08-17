package com.smartlab.service.impl;

import com.smartlab.entity.UserEntity;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailServiceImplTest {

    private static final String EMAIL = "member@example.test";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PermissionService permissionService;
    @InjectMocks
    private AppUserDetailServiceImpl service;

    @Test
    void inactiveAccountProducesDisabledUserDetails() {
        UserEntity user = user(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(permissionService.getEffectivePermissionCodes(user)).thenReturn(Set.of());
        when(permissionService.getRoleCodes(user)).thenReturn(Set.of("MEMBER"));

        UserDetails details = service.loadUserByUsername(EMAIL);

        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void inactiveAssignedRoleProducesDisabledUserDetails() {
        UserEntity user = user(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(permissionService.getEffectivePermissionCodes(user)).thenReturn(Set.of());
        when(permissionService.getRoleCodes(user)).thenReturn(Set.of("MEMBER"));
        when(permissionService.hasInactiveAssignedRole(user)).thenReturn(true);

        UserDetails details = service.loadUserByUsername(EMAIL);

        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void activeAccountWithActiveRolesProducesEnabledUserDetails() {
        UserEntity user = user(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(permissionService.getEffectivePermissionCodes(user)).thenReturn(Set.of("PROFILE_READ"));
        when(permissionService.getRoleCodes(user)).thenReturn(Set.of("MEMBER"));
        when(permissionService.hasInactiveAssignedRole(user)).thenReturn(false);

        UserDetails details = service.loadUserByUsername(EMAIL);

        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities()).extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder("PROFILE_READ", "ROLE_MEMBER");
    }

    private static UserEntity user(boolean active) {
        return UserEntity.builder()
                .id(1L)
                .userId("member-1")
                .email(EMAIL)
                .password("encoded")
                .isActive(active)
                .isAccountVerified(false)
                .build();
    }
}
