package com.smartlab.config;

import com.smartlab.service.ChatWebSocketAuthenticationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatStompAuthInterceptorTest {
    @Mock private ChatWebSocketAuthenticationService authenticationService;

    @Test
    void connectUsesNativeJwtAndStoresAuthenticatedPrincipal() {
        ChatStompAuthInterceptor interceptor = new ChatStompAuthInterceptor(authenticationService);
        Authentication authentication = new UsernamePasswordAuthenticationToken("alice@example.test", null);
        when(authenticationService.authenticate("Bearer token")).thenReturn(authentication);

        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.addNativeHeader("Authorization", "Bearer token");
        headers.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertThat(StompHeaderAccessor.wrap(result).getUser()).isSameAs(authentication);
    }

    @Test
    void clientSuppliedUserTopicIsRejected() {
        ChatStompAuthInterceptor interceptor = new ChatStompAuthInterceptor(authenticationService);
        Authentication authentication = new UsernamePasswordAuthenticationToken("alice@example.test", null);
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination("/topic/user/bob");
        headers.setUser(authentication);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authenticated chat user queue");
    }

    @Test
    void onlyAuthenticatedUserQueueIsAccepted() {
        ChatStompAuthInterceptor interceptor = new ChatStompAuthInterceptor(authenticationService);
        Authentication authentication = new UsernamePasswordAuthenticationToken("alice@example.test", null);
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination("/user/queue/chat");
        headers.setUser(authentication);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

        assertThat(interceptor.preSend(message, null)).isNotNull();
    }

    @Test
    void disconnectIsAllowedAfterWebSocketSessionHasBeenClosed() {
        ChatStompAuthInterceptor interceptor = new ChatStompAuthInterceptor(authenticationService);
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

        assertThat(interceptor.preSend(message, null)).isNotNull();
    }

    @Test
    void subsequentFrameRestoresPrincipalFromServerSessionAttributes() {
        ChatStompAuthInterceptor interceptor = new ChatStompAuthInterceptor(authenticationService);
        Authentication authentication = new UsernamePasswordAuthenticationToken("alice@example.test", null);
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination("/user/queue/chat");
        headers.setSessionAttributes(Map.of(ChatHandshakeInterceptor.AUTHENTICATION_ATTRIBUTE, authentication));
        headers.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertThat(StompHeaderAccessor.wrap(result).getUser()).isSameAs(authentication);
    }
}
