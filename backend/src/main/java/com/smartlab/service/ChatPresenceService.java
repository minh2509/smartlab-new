package com.smartlab.service;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ChatPresenceService {
    private final Map<String, AtomicInteger> connections = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastActive = new ConcurrentHashMap<>();
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    @EventListener
    public void connected(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        if (principal == null || accessor.getSessionId() == null) return;
        sessions.put(accessor.getSessionId(), principal.getName());
        connections.computeIfAbsent(principal.getName(), ignored -> new AtomicInteger()).incrementAndGet();
        lastActive.put(principal.getName(), Instant.now());
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        String identity = sessions.remove(event.getSessionId());
        if (identity == null) return;
        connections.computeIfPresent(identity, (ignored, count) -> count.decrementAndGet() <= 0 ? null : count);
        lastActive.put(identity, Instant.now());
    }

    public boolean isOnline(String identity) {
        AtomicInteger count = identity == null ? null : connections.get(identity);
        return count != null && count.get() > 0;
    }

    public Instant lastActiveAt(String identity) {
        return identity == null ? null : lastActive.get(identity);
    }
}
