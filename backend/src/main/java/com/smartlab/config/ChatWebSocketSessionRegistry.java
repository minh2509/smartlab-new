package com.smartlab.config;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Associates a transport session with the authenticated, revocable login session. */
@Component
public class ChatWebSocketSessionRegistry {
    private final Map<String, String> loginSessions = new ConcurrentHashMap<>();

    @EventListener
    public void connected(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (accessor.getSessionId() == null) return;
        if (accessor.getUser() instanceof Authentication authentication
                && authentication.getDetails() instanceof String loginSessionId) {
            loginSessions.put(accessor.getSessionId(), loginSessionId);
        }
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        loginSessions.remove(event.getSessionId());
    }

    public String loginSessionId(String webSocketSessionId) {
        return webSocketSessionId == null ? null : loginSessions.get(webSocketSessionId);
    }
}
