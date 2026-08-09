package com.smartlab.service.impl;

import com.smartlab.entity.UserEntity;
import com.smartlab.entity.UserSessionEntity;
import com.smartlab.repo.UserRepository;
import com.smartlab.repo.UserSessionRepository;
import com.smartlab.service.TokenHashService;
import com.smartlab.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${smartlab.session.max-active:3}")
    private int maxActiveSessions;

    @Value("${smartlab.session.ttl-days:30}")
    private long sessionTtlDays;

    @Transactional
    @Override
    public UserSessionEntity createSession(UserEntity user, String userAgent, String ipAddress) {
        List<UserSessionEntity> activeSessions =
                userSessionRepository.findByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(user.getId());
        Instant now = Instant.now();
        while (activeSessions.size() >= maxActiveSessions) {
            UserSessionEntity oldest = activeSessions.remove(0);
            oldest.setRevokedAt(now);
            userSessionRepository.save(oldest);
        }

        String refreshToken = randomToken();
        UserSessionEntity session = UserSessionEntity.builder()
                .sessionId(UUID.randomUUID().toString())
                .user(user)
                .refreshTokenHash(tokenHashService.sha256(refreshToken))
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .expiresAt(now.plus(sessionTtlDays, ChronoUnit.DAYS))
                .lastSeenAt(now)
                .build();
        return userSessionRepository.save(session);
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
    public void revokeAllByEmail(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        userSessionRepository.revokeAllActiveByUserId(user.getId(), Instant.now());
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
