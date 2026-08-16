package com.smartlab.service.impl;

import com.smartlab.entity.UserEntity;
import com.smartlab.entity.UserSessionEntity;
import com.smartlab.repo.UserRepository;
import com.smartlab.repo.UserSessionRepository;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.TokenHashService;
import com.smartlab.service.UserSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSessionServiceImplTest {

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenHashService tokenHashService;

    @Mock
    private AppUserDetailService appUserDetailService;

    private UserSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserSessionServiceImpl(
                userSessionRepository,
                userRepository,
                tokenHashService,
                appUserDetailService
        );
        ReflectionTestUtils.setField(service, "maxActiveSessions", 3);
        ReflectionTestUtils.setField(service, "sessionTtlDays", 30L);
    }

    @Test
    void fourthActiveSessionRevokesOldestBeforeCreatingReplacement() {
        UserEntity user = persistedUser();

        when(userSessionRepository.save(any(UserSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserSessionEntity oldest = session(user, "s1", Instant.now().plusSeconds(3600));
        UserSessionEntity second = session(user, "s2", Instant.now().plusSeconds(3600));
        UserSessionEntity third = session(user, "s3", Instant.now().plusSeconds(3600));

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userSessionRepository.findActiveByUserIdOrderByOldest(eq(1L), any(Instant.class)))
                .thenReturn(new ArrayList<>(List.of(oldest, second, third)));
        when(tokenHashService.sha256(anyString())).thenReturn("a".repeat(64));

        UserSessionService.SessionCredential credential =
                service.createSessionCredential(user, "agent", "127.0.0.1");

        assertThat(oldest.getRevokedAt()).isNotNull();
        assertThat(second.getRevokedAt()).isNull();
        assertThat(third.getRevokedAt()).isNull();

        assertThat(credential.refreshToken()).isNotBlank();
        assertThat(credential.session().getSessionId()).isNotBlank();
        assertThat(credential.session().getUser()).isSameAs(user);
        assertThat(credential.session().getRefreshTokenHash()).isEqualTo("a".repeat(64));

        verify(userRepository).findByIdForUpdate(1L);
        verify(userSessionRepository)
                .findActiveByUserIdOrderByOldest(eq(1L), any(Instant.class));
        verify(userSessionRepository).save(oldest);
    }

    @Test
    void fewerThanThreeActiveSessionsDoNotRevokeAnything() {
        UserEntity user = persistedUser();

        when(userSessionRepository.save(any(UserSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserSessionEntity first = session(user, "s1", Instant.now().plusSeconds(3600));
        UserSessionEntity second = session(user, "s2", Instant.now().plusSeconds(3600));

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userSessionRepository.findActiveByUserIdOrderByOldest(eq(1L), any(Instant.class)))
                .thenReturn(new ArrayList<>(List.of(first, second)));
        when(tokenHashService.sha256(anyString())).thenReturn("b".repeat(64));

        service.createSessionCredential(user, "agent", "127.0.0.1");

        assertThat(first.getRevokedAt()).isNull();
        assertThat(second.getRevokedAt()).isNull();
    }

    @Test
    void refreshRotationUsesUserThenSessionWriteLockAndKeepsSessionIdentity() {
        UserEntity user = persistedUser();

        UserSessionEntity session = session(user, "session-1", Instant.now().plusSeconds(3600));
        session.setRefreshTokenHash("old-hash");

        UserDetails userDetails = User.withUsername(user.getEmail())
                .password("encoded")
                .authorities("PROFILE_READ")
                .build();

        when(tokenHashService.sha256(anyString()))
                .thenAnswer(invocation ->
                        "old-refresh".equals(invocation.getArgument(0))
                                ? "old-hash"
                                : "new-hash"
                );

        when(userSessionRepository.findByRefreshTokenHash("old-hash"))
                .thenReturn(Optional.of(session));
        when(userRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(user));
        when(userSessionRepository.findByRefreshTokenHashForUpdate("old-hash"))
                .thenReturn(Optional.of(session));
        when(appUserDetailService.loadUserByUsername(user.getEmail()))
                .thenReturn(userDetails);
        when(userSessionRepository.saveAndFlush(session))
                .thenReturn(session);

        UserSessionService.RefreshCredential rotated =
                service.rotateRefreshToken("old-refresh");

        assertThat(rotated.session().getSessionId()).isEqualTo("session-1");
        assertThat(rotated.refreshToken()).isNotBlank();
        assertThat(rotated.refreshToken()).isNotEqualTo("old-refresh");
        assertThat(rotated.session().getRefreshTokenHash()).isEqualTo("new-hash");
        assertThat(rotated.userDetails()).isSameAs(userDetails);

        InOrder order = inOrder(userSessionRepository, userRepository);
        order.verify(userSessionRepository).findByRefreshTokenHash("old-hash");
        order.verify(userRepository).findByIdForUpdate(1L);
        order.verify(userSessionRepository).findByRefreshTokenHashForUpdate("old-hash");
    }

    @Test
    void disabledAccountRejectsRefreshBeforeTokenRotation() {
        UserEntity user = persistedUser();

        UserSessionEntity session = session(user, "session-1", Instant.now().plusSeconds(3600));
        session.setRefreshTokenHash("old-hash");

        UserDetails disabled = User.withUsername(user.getEmail())
                .password("encoded")
                .disabled(true)
                .authorities("PROFILE_READ")
                .build();

        when(tokenHashService.sha256("old-refresh")).thenReturn("old-hash");
        when(userSessionRepository.findByRefreshTokenHash("old-hash"))
                .thenReturn(Optional.of(session));
        when(userRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(user));
        when(userSessionRepository.findByRefreshTokenHashForUpdate("old-hash"))
                .thenReturn(Optional.of(session));
        when(appUserDetailService.loadUserByUsername(user.getEmail()))
                .thenReturn(disabled);

        assertThatThrownBy(() -> service.rotateRefreshToken("old-refresh"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid refresh token");

        assertThat(session.getRefreshTokenHash()).isEqualTo("old-hash");
        verify(userSessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void refreshBasedLogoutRevokesCurrentRefreshSession() {
        UserEntity user = persistedUser();

        UserSessionEntity session = session(user, "session-logout", Instant.now().plusSeconds(3600));
        session.setRefreshTokenHash("current-hash");

        when(tokenHashService.sha256("current-refresh")).thenReturn("current-hash");
        when(userSessionRepository.findByRefreshTokenHash("current-hash"))
                .thenReturn(Optional.of(session));
        when(userRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(user));
        when(userSessionRepository.findByRefreshTokenHashForUpdate("current-hash"))
                .thenReturn(Optional.of(session));
        when(userSessionRepository.save(session))
                .thenReturn(session);

        service.revokeByRefreshToken("current-refresh");

        assertThat(session.getRevokedAt()).isNotNull();
        verify(userSessionRepository).save(session);

        InOrder order = inOrder(userSessionRepository, userRepository);
        order.verify(userSessionRepository).findByRefreshTokenHash("current-hash");
        order.verify(userRepository).findByIdForUpdate(1L);
        order.verify(userSessionRepository).findByRefreshTokenHashForUpdate("current-hash");
    }

    @Test
    void refreshBasedLogoutDoesNotRevokeIfTokenRotatedBeforeWriteLock() {
        UserEntity user = persistedUser();

        UserSessionEntity candidate =
                session(user, "session-logout-race", Instant.now().plusSeconds(3600));
        candidate.setRefreshTokenHash("old-hash");

        when(tokenHashService.sha256("old-refresh")).thenReturn("old-hash");
        when(userSessionRepository.findByRefreshTokenHash("old-hash"))
                .thenReturn(Optional.of(candidate));
        when(userRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(user));

        // Simulates another transaction rotating old-hash while this
        // logout waits for the per-user write lock.
        when(userSessionRepository.findByRefreshTokenHashForUpdate("old-hash"))
                .thenReturn(Optional.empty());

        service.revokeByRefreshToken("old-refresh");

        assertThat(candidate.getRevokedAt()).isNull();
        verify(userSessionRepository, never()).save(candidate);
    }

    @Test
    void expiredRefreshSessionIsRejectedWithoutRotation() {
        UserEntity user = persistedUser();

        UserSessionEntity session = session(user, "session-1", Instant.now().minusSeconds(60));
        session.setRefreshTokenHash("old-hash");

        when(tokenHashService.sha256("old-refresh")).thenReturn("old-hash");
        when(userSessionRepository.findByRefreshTokenHash("old-hash"))
                .thenReturn(Optional.of(session));
        when(userRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(user));
        when(userSessionRepository.findByRefreshTokenHashForUpdate("old-hash"))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.rotateRefreshToken("old-refresh"))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(session.getRefreshTokenHash()).isEqualTo("old-hash");
        verifyNoInteractions(appUserDetailService);
        verify(userSessionRepository, never()).saveAndFlush(any());
    }

    private UserEntity persistedUser() {
        return UserEntity.builder()
                .id(1L)
                .userId("member-1")
                .name("Member")
                .email("member@example.test")
                .password("encoded")
                .isActive(true)
                .isAccountVerified(true)
                .build();
    }

    private UserSessionEntity session(UserEntity user, String sessionId, Instant expiresAt) {
        return UserSessionEntity.builder()
                .id((long) Math.abs(sessionId.hashCode()))
                .sessionId(sessionId)
                .user(user)
                .refreshTokenHash(("hash-" + sessionId + "-").repeat(8).substring(0, 64))
                .expiresAt(expiresAt)
                .lastSeenAt(Instant.now())
                .build();
    }
}
