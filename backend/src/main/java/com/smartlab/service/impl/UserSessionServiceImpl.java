package com.smartlab.service.impl;

import com.smartlab.entity.UserEntity;
import com.smartlab.entity.UserSessionEntity;
import com.smartlab.repo.UserRepository;
import com.smartlab.repo.UserSessionRepository;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.TokenHashService;
import com.smartlab.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserSessionServiceImpl implements UserSessionService {
    private final UserSessionRepository userSessionRepository;
    private final UserRepository userRepository;
    private final TokenHashService tokenHashService;
    private final AppUserDetailService appUserDetailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${smartlab.session.max-active:3}")
    private int maxActiveSessions;

    @Value("${smartlab.session.ttl-days:30}")
    private long sessionTtlDays;

    @Transactional
    @Override
    public UserSessionEntity createSession(UserEntity user, String userAgent, String ipAddress) {
        return createSessionCredentialInternal(user, userAgent, ipAddress).session();
    }

    @Transactional
    @Override
    public SessionCredential createSessionCredential(UserEntity user, String userAgent, String ipAddress) {
        return createSessionCredentialInternal(user, userAgent, ipAddress);
    }

    private SessionCredential createSessionCredentialInternal(
            UserEntity user,
            String userAgent,
            String ipAddress
    ) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Persisted user is required");
        }
        if (maxActiveSessions < 1) {
            throw new IllegalStateException("smartlab.session.max-active must be at least 1");
        }
        if (sessionTtlDays < 1) {
            throw new IllegalStateException("smartlab.session.ttl-days must be at least 1");
        }

        UserEntity lockedUser = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + user.getId()));

        Instant now = Instant.now();
        List<UserSessionEntity> activeSessions =
                userSessionRepository.findActiveByUserIdOrderByOldest(lockedUser.getId(), now);

        while (activeSessions.size() >= maxActiveSessions) {
            UserSessionEntity oldest = activeSessions.remove(0);
            oldest.setRevokedAt(now);
            userSessionRepository.save(oldest);
        }

        String refreshToken = randomToken();
        UserSessionEntity session = UserSessionEntity.builder()
                .sessionId(UUID.randomUUID().toString())
                .user(lockedUser)
                .refreshTokenHash(tokenHashService.sha256(refreshToken))
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .expiresAt(now.plus(sessionTtlDays, ChronoUnit.DAYS))
                .lastSeenAt(now)
                .build();

        return new SessionCredential(userSessionRepository.save(session), refreshToken);
    }

    @Transactional
    @Override
    public RefreshCredential rotateRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw invalidRefreshToken();
        }

        String refreshTokenHash = tokenHashService.sha256(refreshToken);

        UserSessionEntity candidate = userSessionRepository
                .findByRefreshTokenHash(refreshTokenHash)
                .orElseThrow(this::invalidRefreshToken);

        UserEntity lockedUser = userRepository.findByIdForUpdate(candidate.getUser().getId())
                .orElseThrow(this::invalidRefreshToken);

        UserSessionEntity session = userSessionRepository
                .findByRefreshTokenHashForUpdate(refreshTokenHash)
                .orElseThrow(this::invalidRefreshToken);

        if (!session.getUser().getId().equals(lockedUser.getId())) {
            throw invalidRefreshToken();
        }

        Instant now = Instant.now();
        if (session.getRevokedAt() != null
                || session.getExpiresAt() == null
                || !session.getExpiresAt().isAfter(now)) {
            throw invalidRefreshToken();
        }

        UserDetails userDetails;
        try {
            userDetails = appUserDetailService.loadUserByUsername(lockedUser.getEmail());
        } catch (Exception ex) {
            throw invalidRefreshToken();
        }

        if (!userDetails.isEnabled()
                || !userDetails.isAccountNonLocked()
                || !userDetails.isAccountNonExpired()
                || !userDetails.isCredentialsNonExpired()) {
            throw invalidRefreshToken();
        }

        String rotatedRefreshToken = randomToken();
        session.setRefreshTokenHash(tokenHashService.sha256(rotatedRefreshToken));
        session.setLastSeenAt(now);

        UserSessionEntity saved = userSessionRepository.saveAndFlush(session);

        return new RefreshCredential(
                saved,
                rotatedRefreshToken,
                userDetails
        );
    }

    @Transactional(readOnly = true)
    @Override
    public boolean isSessionActive(String sessionId) {
        return userSessionRepository.findBySessionIdAndRevokedAtIsNull(sessionId)
                .filter(session -> session.getExpiresAt().isAfter(Instant.now()))
                .isPresent();
    }

    @Transactional
    @Override
    public void touchSession(String sessionId) {
        userSessionRepository.findBySessionIdAndRevokedAtIsNull(sessionId)
                .filter(session -> session.getExpiresAt().isAfter(Instant.now()))
                .ifPresent(session -> {
                    session.setLastSeenAt(Instant.now());
                    userSessionRepository.save(session);
                });
    }

    @Transactional
    @Override
    public void revokeSession(String sessionId) {
        userSessionRepository.findBySessionIdAndRevokedAtIsNull(sessionId)
                .ifPresent(session -> {
                    session.setRevokedAt(Instant.now());
                    userSessionRepository.save(session);
                });
    }

    @Transactional
    @Override
    public void revokeByRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        String refreshTokenHash = tokenHashService.sha256(refreshToken);

        UserSessionEntity candidate = userSessionRepository.findByRefreshTokenHash(refreshTokenHash)
                .orElse(null);
        if (candidate == null) {
            return;
        }

        UserEntity lockedUser = userRepository.findByIdForUpdate(candidate.getUser().getId())
                .orElse(null);
        if (lockedUser == null) {
            return;
        }

        UserSessionEntity session = userSessionRepository
                .findByRefreshTokenHashForUpdate(refreshTokenHash)
                .orElse(null);

        if (session == null || !session.getUser().getId().equals(lockedUser.getId())) {
            return;
        }

        if (session.getRevokedAt() == null) {
            session.setRevokedAt(Instant.now());
            userSessionRepository.save(session);
        }
    }

    @Transactional
    @Override
    public void revokeAllByEmail(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        userSessionRepository.revokeAllActiveByUserId(user.getId(), Instant.now());
    }

    private BadCredentialsException invalidRefreshToken() {
        return new BadCredentialsException("Invalid refresh token");
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
