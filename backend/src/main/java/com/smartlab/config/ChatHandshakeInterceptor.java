package com.smartlab.config;

import com.smartlab.service.ChatWebSocketAuthenticationService;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChatHandshakeInterceptor implements HandshakeInterceptor {
    static final String AUTHENTICATION_ATTRIBUTE = ChatHandshakeInterceptor.class.getName() + ".authentication";

    private final ChatWebSocketAuthenticationService authenticationService;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String token = resolveToken(request);
        if (token == null) {
            return true;
        }
        Authentication authentication = authenticationService.authenticate(token);
        attributes.put(AUTHENTICATION_ATTRIBUTE, authentication);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }

    private String resolveToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            return authorization;
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            Cookie[] cookies = servletRequest.getServletRequest().getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("jwt".equals(cookie.getName())) {
                        return cookie.getValue();
                    }
                }
            }
        }
        return null;
    }
}
