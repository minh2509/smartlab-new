package com.smartlab.service;

import com.smartlab.entity.UserEntity;
import com.smartlab.entity.UserSessionEntity;
import org.springframework.security.core.userdetails.UserDetails;

public interface UserSessionService {
    record SessionCredential(UserSessionEntity session, String refreshToken) {
    }

    record RefreshCredential(
            UserSessionEntity session,
            String refreshToken,
            UserDetails userDetails
    ) {
    }

    UserSessionEntity createSession(UserEntity user, String userAgent, String ipAddress);

    SessionCredential createSessionCredential(UserEntity user, String userAgent, String ipAddress);

    RefreshCredential rotateRefreshToken(String refreshToken);

    boolean isSessionActive(String sessionId);

    void touchSession(String sessionId);

    void revokeSession(String sessionId);

    void revokeByRefreshToken(String refreshToken);

    void revokeAllByEmail(String email);
}
