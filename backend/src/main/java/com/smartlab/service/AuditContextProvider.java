package com.smartlab.service;

import com.smartlab.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@RequiredArgsConstructor
public class AuditContextProvider {
    private final UserRepository userRepository;

    public AuditContext current() {
        return new AuditContext(resolveActorUserId(), resolveIpAddress(), resolveUserAgent());
    }

    private Long resolveActorUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return null;
        String principal = authentication.getName();
        if (principal == null || principal.isBlank() || "anonymousUser".equals(principal)) return null;
        return userRepository.findByEmail(principal).map(user -> user.getId()).orElse(null);
    }

    private String resolveIpAddress() {
        HttpServletRequest request = request();
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null) {
            String first = forwarded.split(",", 2)[0].trim();
            if (usableIp(first)) return first;
        }
        String remote = request.getRemoteAddr();
        return usableIp(remote) ? remote : null;
    }

    private String resolveUserAgent() {
        HttpServletRequest request = request();
        return request == null ? null : request.getHeader("User-Agent");
    }

    private static boolean usableIp(String value) {
        return value != null && !value.isBlank() && value.length() <= 45;
    }

    private static HttpServletRequest request() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }

    public record AuditContext(Long actorUserId, String ipAddress, String userAgent) {
    }
}
