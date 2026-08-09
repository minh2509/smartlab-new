package com.smartlab.filter;

import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.UserSessionService;
import com.smartlab.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {

    private final AppUserDetailService appUserDetailService;
    private final JwtUtil jwtUtil;
    private final UserSessionService userSessionService;

    private static final List<String> PUBLIC_URLS = List.of(
            "/login",
            "/send-reset-otp",
            "/verify-reset-otp",
            "/reset-password",
            "/logout",
            "/invitations/accept",
            "/swagger-ui",
            "/v3/api-docs"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        final String path = request.getServletPath();
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = resolveToken(request);
        if (jwt != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String email = jwtUtil.extractEmail(jwt);
                String sessionId = jwtUtil.extractSessionId(jwt);
                UserDetails userDetails = appUserDetailService.loadUserByUsername(email);
                if (jwtUtil.validateToken(jwt, userDetails) && userSessionService.isSessionActive(sessionId)) {
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authenticationToken.setDetails(new WebAuthenticationDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    userSessionService.touchSession(sessionId);
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_URLS.stream().anyMatch(path::startsWith);
    }

    private String resolveToken(HttpServletRequest request) {
        final String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if ("jwt".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
