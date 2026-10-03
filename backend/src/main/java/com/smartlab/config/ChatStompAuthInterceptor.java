package com.smartlab.config;

import com.smartlab.service.ChatWebSocketAuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChatStompAuthInterceptor implements ChannelInterceptor {
    private static final String USER_QUEUE = "/user/queue/chat";
    private static final String APP_PREFIX = "/app/chat/";

    private final ChatWebSocketAuthenticationService authenticationService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            throw new IllegalStateException("STOMP headers are required");
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            String nativeAuthorization = accessor.getFirstNativeHeader("Authorization");
            Authentication authentication = nativeAuthorization == null
                    ? authenticationFromHandshake(accessor)
                    : authenticationService.authenticate(nativeAuthorization);
            if (authentication == null) {
                authentication = authenticationService.authenticate(null);
            }
            accessor.setUser(authentication);
            Map<String, Object> attributes = accessor.getSessionAttributes();
            if (attributes != null) {
                attributes.put(ChatHandshakeInterceptor.AUTHENTICATION_ATTRIBUTE, authentication);
            }
        } else if (command != StompCommand.DISCONNECT && accessor.getUser() == null) {
            Authentication authentication = authenticationFromHandshake(accessor);
            if (authentication == null) {
                throw new IllegalStateException("Authenticated STOMP CONNECT is required");
            }
            accessor.setUser(authentication);
        }

        if (command == StompCommand.SUBSCRIBE && !USER_QUEUE.equals(accessor.getDestination())) {
            throw new IllegalStateException("Only the authenticated chat user queue may be subscribed to");
        }
        if (command == StompCommand.SEND
                && (accessor.getDestination() == null || !accessor.getDestination().startsWith(APP_PREFIX))) {
            throw new IllegalStateException("Unsupported chat application destination");
        }
        return message;
    }

    private Authentication authenticationFromHandshake(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            return null;
        }
        Object authentication = attributes.get(ChatHandshakeInterceptor.AUTHENTICATION_ATTRIBUTE);
        return authentication instanceof Authentication value ? value : null;
    }
}
