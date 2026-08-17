package com.smartlab.service.impl;

import com.smartlab.dto.request.AdminUpdateMemberProfileRequest;
import com.smartlab.dto.request.UpdateMemberProfileRequest;
import com.smartlab.entity.MemberProfileEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.repo.MemberProfileRepository;
import com.smartlab.repo.ResearchFieldRepository;
import com.smartlab.repo.StoredFileRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberProfileServiceImplTest {
    @Mock UserRepository userRepository;
    @Mock MemberProfileRepository memberProfileRepository;
    @Mock StoredFileRepository storedFileRepository;
    @Mock ResearchFieldRepository researchFieldRepository;
    @Mock PermissionService permissionService;
    @InjectMocks MemberProfileServiceImpl service;

    @Test
    void publicEndpointRejectsInactiveStatus() {
        assertThatThrownBy(() -> service.listMembers(null, null, "INACTIVE"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void publicMemberResponseDoesNotExposeLoginEmailOrPhone() {
        UserEntity user = user(1L, true, true);
        MemberProfileEntity profile = MemberProfileEntity.create(user);
        profile.setPhone("0900000000");
        profile.setPublicEmail("public@lab.test");
        when(memberProfileRepository.findByActiveStatusOrderByFeaturedOrderAscIdAsc("ACTIVE"))
                .thenReturn(List.of(profile));

        var response = service.listMembers(null, null, null).getFirst();

        assertThat(response.getEmail()).isNull();
        assertThat(response.getPhone()).isNull();
        assertThat(response.getPublicEmail()).isEqualTo("public@lab.test");
    }

    @Test
    void adminListBackfillsMissingProfile() {
        UserEntity user = user(3L, true, true);
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(memberProfileRepository.findByUserId(3L)).thenReturn(Optional.empty());
        when(memberProfileRepository.save(any(MemberProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.listAllMembers();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getEmail()).isEqualTo("member@lab.test");
        assertThat(response.getFirst().getActiveStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void ownProfileIncludesEffectiveRolesAndPermissions() {
        UserEntity user = user(4L, true, true);
        MemberProfileEntity profile = MemberProfileEntity.create(user);
        when(userRepository.findByEmail("member@lab.test")).thenReturn(Optional.of(user));
        when(memberProfileRepository.findByUserId(4L)).thenReturn(Optional.of(profile));
        when(permissionService.getRoleCodes(user)).thenReturn(Set.of("MEMBER"));
        when(permissionService.getEffectivePermissionCodes(user))
                .thenReturn(Set.of("PROFILE_READ", "PROFILE_UPDATE"));

        var response = service.getOwnProfile("member@lab.test");

        assertThat(response.getRoles()).containsExactly("MEMBER");
        assertThat(response.getPermissions()).containsExactlyInAnyOrder("PROFILE_READ", "PROFILE_UPDATE");
    }

    @Test
    void adminCanSetJoinedLabDate() {
        UserEntity user = user(5L, true, true);
        MemberProfileEntity profile = MemberProfileEntity.create(user);
        AdminUpdateMemberProfileRequest request = new AdminUpdateMemberProfileRequest();
        request.setJoinedLabAt(LocalDate.of(2025, 9, 1));
        when(userRepository.findByUserId("user-5")).thenReturn(Optional.of(user));
        when(memberProfileRepository.findByUserId(5L)).thenReturn(Optional.of(profile));
        when(memberProfileRepository.save(profile)).thenReturn(profile);

        var response = service.updateMember("user-5", request);

        assertThat(response.getJoinedLabAt()).isEqualTo(LocalDate.of(2025, 9, 1));
    }

    @Test
    void ownProfileRejectsPrivateAvatarBecausePublicDirectoryCannotRenderIt() {
        UserEntity user = user(6L, true, true);
        MemberProfileEntity profile = MemberProfileEntity.create(user);
        StoredFileEntity avatar = StoredFileEntity.builder()
                .id(10L)
                .ownerUser(user)
                .mimeType("image/png")
                .accessScope("PRIVATE")
                .build();
        UpdateMemberProfileRequest request = new UpdateMemberProfileRequest();
        request.setAvatarFileId(10L);
        when(userRepository.findByEmail("member@lab.test")).thenReturn(Optional.of(user));
        when(memberProfileRepository.findByUserId(6L)).thenReturn(Optional.of(profile));
        when(storedFileRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(avatar));

        assertThatThrownBy(() -> service.updateOwnProfile("member@lab.test", request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private UserEntity user(Long id, boolean active, boolean verified) {
        return UserEntity.builder()
                .id(id)
                .userId("user-" + id)
                .name("Member")
                .email("member@lab.test")
                .isActive(active)
                .isAccountVerified(verified)
                .build();
    }
}
