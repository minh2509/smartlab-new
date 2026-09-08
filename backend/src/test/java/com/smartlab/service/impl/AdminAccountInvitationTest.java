package com.smartlab.service.impl;

import com.smartlab.dto.request.InvitationAcceptRequest;
import com.smartlab.dto.response.AccountResponse;
import com.smartlab.entity.AccountInvitationEntity;
import com.smartlab.entity.MemberProfileEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.InvitationStatus;
import com.smartlab.repo.AccountInvitationRepository;
import com.smartlab.repo.EmailOutboxRepository;
import com.smartlab.repo.MemberProfileRepository;
import com.smartlab.repo.PermissionRepository;
import com.smartlab.repo.RoleRepository;
import com.smartlab.repo.UserPermissionOverrideRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.repo.UserRoleRepository;
import com.smartlab.service.AuditService;
import com.smartlab.service.PermissionService;
import com.smartlab.service.TokenHashService;
import com.smartlab.service.UserSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccountInvitationTest {

    private static final String EMAIL = "invited@example.test";
    private static final String RAW_TOKEN = "invite-token";
    private static final String TOKEN_HASH = "a".repeat(64);
    private static final String PASSWORD = "Member@123";

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserPermissionOverrideRepository userPermissionOverrideRepository;
    @Mock private AccountInvitationRepository accountInvitationRepository;
    @Mock private MemberProfileRepository memberProfileRepository;
    @Mock private PermissionService permissionService;
    @Mock private EmailOutboxRepository emailOutboxRepository;
    @Mock private AccountInvitationOutboxStateService outboxStateService;
    @Mock private UserSessionService userSessionService;
    @Mock private TokenHashService tokenHashService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;

    private AdminAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAccountServiceImpl(
                userRepository, roleRepository, permissionRepository, userRoleRepository,
                userPermissionOverrideRepository, accountInvitationRepository, memberProfileRepository,
                permissionService, emailOutboxRepository, outboxStateService, userSessionService, tokenHashService, passwordEncoder, auditService
        );
    }

    @Test
    void acceptValidInvitationActivatesAndVerifiesAccount() {
        UserEntity user = invitedUser();
        MemberProfileEntity profile = MemberProfileEntity.create(user);
        AccountInvitationEntity invitation = invitation(InvitationStatus.PENDING, Instant.now().plusSeconds(3600));
        when(tokenHashService.sha256(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(accountInvitationRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(invitation));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(memberProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(profile));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded-new-password");
        when(permissionService.getRoleCodes(user)).thenReturn(Set.of("MEMBER"));
        when(permissionService.getEffectivePermissionCodes(user)).thenReturn(Set.of("PROFILE_READ"));

        AccountResponse response = service.acceptInvite(request());

        verify(passwordEncoder).encode(PASSWORD);
        assertThat(user.getPassword()).isEqualTo("encoded-new-password");
        assertThat(user.getIsActive()).isTrue();
        assertThat(user.getIsAccountVerified()).isTrue();
        assertThat(profile.getActiveStatus()).isEqualTo("ACTIVE");
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(invitation.getAcceptedAt()).isNotNull();
        verify(userSessionService).revokeAllByEmail(EMAIL);
        verify(outboxStateService).markActivated(51L);
        assertThat(response.getEmail()).isEqualTo(EMAIL);
        assertThat(response.getIsActive()).isTrue();
        assertThat(response.getIsAccountVerified()).isTrue();
    }

    @Test
    void invalidInvitationTokenReturnsBadRequest() {
        when(tokenHashService.sha256(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(accountInvitationRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

        assertBadRequest(() -> service.acceptInvite(request()));

        verifyNoInteractions(userRepository, memberProfileRepository, userSessionService, passwordEncoder);
    }

    @Test
    void alreadyAcceptedInvitationReturnsBadRequest() {
        AccountInvitationEntity invitation = invitation(InvitationStatus.ACCEPTED, Instant.now().plusSeconds(3600));
        when(tokenHashService.sha256(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(accountInvitationRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(invitation));

        assertBadRequest(() -> service.acceptInvite(request()));

        verify(userRepository, never()).findByEmail(any());
        verify(memberProfileRepository, never()).findByUserId(any());
        verify(userSessionService, never()).revokeAllByEmail(any());
        verify(accountInvitationRepository, never()).save(any());
    }

    @Test
    void expiredInvitationMarksEntityExpiredBeforeThrowing() {
        AccountInvitationEntity invitation = invitation(InvitationStatus.PENDING, Instant.now().minusSeconds(1));
        when(tokenHashService.sha256(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(accountInvitationRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(invitation));

        assertBadRequest(() -> service.acceptInvite(request()));

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
        verify(accountInvitationRepository).save(invitation);
        verifyNoInteractions(userRepository, memberProfileRepository, userSessionService, passwordEncoder);
    }

    private static InvitationAcceptRequest request() {
        InvitationAcceptRequest request = new InvitationAcceptRequest();
        request.setToken(RAW_TOKEN);
        request.setPassword(PASSWORD);
        return request;
    }

    private static UserEntity invitedUser() {
        return UserEntity.builder()
                .id(41L)
                .userId("invited-user")
                .name("Invited Member")
                .email(EMAIL)
                .password("temporary-password")
                .isActive(false)
                .isAccountVerified(false)
                .build();
    }

    private static AccountInvitationEntity invitation(InvitationStatus status, Instant expiresAt) {
        return AccountInvitationEntity.builder()
                .id(51L)
                .invitationId("invite-51")
                .email(EMAIL)
                .tokenHash(TOKEN_HASH)
                .status(status)
                .expiresAt(expiresAt)
                .resendCount(0)
                .build();
    }

    private static void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
