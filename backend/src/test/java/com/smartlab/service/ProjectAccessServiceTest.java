package com.smartlab.service;

import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private PermissionService permissionService;
    @InjectMocks private ProjectAccessService service;

    @Test
    void adminCanReadProject() {
        UserEntity admin = user(1L, "admin@test");
        ProjectEntity project = project(7L);
        when(userRepository.findByEmail("admin@test")).thenReturn(Optional.of(admin));
        when(permissionService.getRoleCodes(admin)).thenReturn(Set.of("ADMIN"));

        assertThat(service.requireRead(project, "admin@test")).isSameAs(admin);
    }

    @Test
    void activeMemberNeedsProjectReadPermission() {
        UserEntity member = user(2L, "member@test");
        ProjectEntity project = project(7L);
        when(userRepository.findByEmail("member@test")).thenReturn(Optional.of(member));
        when(permissionService.getRoleCodes(member)).thenReturn(Set.of("MEMBER"));
        when(permissionService.getEffectivePermissionCodes(member)).thenReturn(Set.of());

        assertStatus(() -> service.requireRead(project, "member@test"), HttpStatus.NOT_FOUND);
    }

    @Test
    void adminMustAlsoHaveProjectManageForMutation() {
        UserEntity admin = user(1L, "admin@test");
        ProjectEntity project = project(7L);
        when(userRepository.findByEmail("admin@test")).thenReturn(Optional.of(admin));
        when(permissionService.getRoleCodes(admin)).thenReturn(Set.of("ADMIN"));
        when(permissionService.getEffectivePermissionCodes(admin)).thenReturn(Set.of());

        assertStatus(() -> service.requireManage(project, "admin@test"), HttpStatus.FORBIDDEN);
    }

    @Test
    void activeProjectLeaderCanMutateWithoutGlobalManagePermission() {
        UserEntity leader = user(2L, "leader@test");
        ProjectEntity project = project(7L);
        when(userRepository.findByEmail("leader@test")).thenReturn(Optional.of(leader));
        when(permissionService.getRoleCodes(leader)).thenReturn(Set.of("MEMBER"));
        when(permissionService.getEffectivePermissionCodes(leader)).thenReturn(Set.of());
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                7L, 2L, ProjectRole.LEADER, ProjectMemberStatus.ACTIVE
        )).thenReturn(true);

        assertThat(service.requireManage(project, "leader@test")).isSameAs(leader);
    }

    @Test
    void inactiveAuthenticatedAccountIsUnauthorized() {
        UserEntity member = user(2L, "member@test");
        member.setIsActive(false);
        when(userRepository.findByEmail("member@test")).thenReturn(Optional.of(member));

        assertStatus(() -> service.requireRead(project(7L), "member@test"), HttpStatus.UNAUTHORIZED);
    }

    private UserEntity user(Long id, String email) {
        return UserEntity.builder().id(id).userId("u" + id).name("User").email(email)
                .password("encoded").isActive(true).isAccountVerified(true).build();
    }

    private ProjectEntity project(Long id) {
        ProjectEntity project = org.mockito.Mockito.mock(ProjectEntity.class);
        lenient().when(project.getId()).thenReturn(id);
        return project;
    }

    private void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, HttpStatus status) {
        assertThatThrownBy(callable)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(status);
    }
}
