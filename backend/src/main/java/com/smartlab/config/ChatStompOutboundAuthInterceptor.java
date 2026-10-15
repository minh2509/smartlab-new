package com.smartlab.config;

import com.smartlab.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;


/** Prevent an already connected, revoked session from receiving private chat events. */
@Component
@RequiredArgsConstructor
public class ChatStompOutboundAuthInterceptor implements ChannelInterceptor {
    private final UserSessionService userSessionService;
    private final ChatWebSocketSessionRegistry sessionRegistry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getMessageType() != SimpMessageType.MESSAGE) return message;
        String loginSessionId = sessionRegistry.loginSessionId(accessor.getSessionId());
        return loginSessionId != null && userSessionService.isSessionActive(loginSessionId)
                ? message : null;
    }
}
