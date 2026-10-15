package com.smartlab.config;

import com.smartlab.service.UserSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatStompOutboundAuthInterceptorTest {
    @Mock private UserSessionService userSessionService;
    @Mock private ChatWebSocketSessionRegistry sessionRegistry;

    @Test
    void blocksChatMessageWhenLoginSessionWasRevoked() {
        ChatStompOutboundAuthInterceptor interceptor =
                new ChatStompOutboundAuthInterceptor(userSessionService, sessionRegistry);
        Message<byte[]> message = frame(StompCommand.MESSAGE);
        when(sessionRegistry.loginSessionId("transport-1")).thenReturn("login-1");
        when(userSessionService.isSessionActive("login-1")).thenReturn(false);

        assertThat(interceptor.preSend(message, null)).isNull();
    }

    @Test
    void allowsOnlyAnActiveAuthenticatedChatSession() {
        ChatStompOutboundAuthInterceptor interceptor =
                new ChatStompOutboundAuthInterceptor(userSessionService, sessionRegistry);
        Message<byte[]> message = frame(StompCommand.MESSAGE);
        when(sessionRegistry.loginSessionId("transport-1")).thenReturn("login-1");
        when(userSessionService.isSessionActive("login-1")).thenReturn(true);

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    void rejectsUnknownTransportButKeepsControlFrames() {
        ChatStompOutboundAuthInterceptor interceptor =
                new ChatStompOutboundAuthInterceptor(userSessionService, sessionRegistry);
        assertThat(interceptor.preSend(frame(StompCommand.MESSAGE), null)).isNull();
        Message<byte[]> control = frame(StompCommand.CONNECTED);
        assertThat(interceptor.preSend(control, null)).isSameAs(control);
    }

    private Message<byte[]> frame(StompCommand command) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        headers.setSessionId("transport-1");
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
