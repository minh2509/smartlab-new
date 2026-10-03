package com.smartlab.service;

import com.smartlab.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatWebSocketAuthenticationService {
    private final AppUserDetailService appUserDetailService;
    private final JwtUtil jwtUtil;
    private final UserSessionService userSessionService;

    public Authentication authenticate(String value) {
        String token = normalize(value);
        try {
            String email = jwtUtil.extractEmail(token);
            String sessionId = jwtUtil.extractSessionId(token);
            UserDetails userDetails = appUserDetailService.loadUserByUsername(email);
            if (!userDetails.isEnabled()
                    || !jwtUtil.validateToken(token, userDetails)
                    || !userSessionService.isSessionActive(sessionId)) {
                throw invalid();
            }
            userSessionService.touchSession(sessionId);
            return new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
        } catch (BadCredentialsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw invalid();
        }
        String token = value.trim();
        return token.regionMatches(true, 0, "Bearer ", 0, 7)
                ? token.substring(7).trim()
                : token;
    }

    private BadCredentialsException invalid() {
        return new BadCredentialsException("Invalid chat WebSocket authentication");
    }
}
