package com.smartlab.service;

import com.smartlab.entity.UserEntity;
import com.smartlab.entity.UserSessionEntity;

public interface UserSessionService {
    UserSessionEntity createSession(UserEntity user, String userAgent, String ipAddress);

    boolean isSessionActive(String sessionId);

    void touchSession(String sessionId);

    void revokeSession(String sessionId);

    void revokeAllByEmail(String email);
}
