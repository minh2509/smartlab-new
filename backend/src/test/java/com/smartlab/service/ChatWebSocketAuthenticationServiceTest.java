package com.smartlab.service;

import com.smartlab.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketAuthenticationServiceTest {
    @Mock private AppUserDetailService userDetailsService;
    @Mock private JwtUtil jwtUtil;
    @Mock private UserSessionService sessions;

    @Test
    void validJwtAndActiveSessionBecomeAuthenticatedPrincipal() {
        ChatWebSocketAuthenticationService service =
                new ChatWebSocketAuthenticationService(userDetailsService, jwtUtil, sessions);
        UserDetails details = User.withUsername("alice@example.test")
                .password("ignored")
                .authorities("chat.read")
                .build();
        when(jwtUtil.extractEmail("token")).thenReturn("alice@example.test");
        when(jwtUtil.extractSessionId("token")).thenReturn("session-1");
        when(userDetailsService.loadUserByUsername("alice@example.test")).thenReturn(details);
        when(jwtUtil.validateToken("token", details)).thenReturn(true);
        when(sessions.isSessionActive("session-1")).thenReturn(true);

        var authentication = service.authenticate("Bearer token");

        assertThat(authentication.getName()).isEqualTo("alice@example.test");
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("chat.read");
    }

    @Test
    void revokedSessionIsRejected() {
        ChatWebSocketAuthenticationService service =
                new ChatWebSocketAuthenticationService(userDetailsService, jwtUtil, sessions);
        UserDetails details = User.withUsername("alice@example.test").password("ignored").build();
        when(jwtUtil.extractEmail("token")).thenReturn("alice@example.test");
        when(jwtUtil.extractSessionId("token")).thenReturn("revoked");
        when(userDetailsService.loadUserByUsername("alice@example.test")).thenReturn(details);
        when(jwtUtil.validateToken("token", details)).thenReturn(true);
        when(sessions.isSessionActive("revoked")).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate("token"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid chat WebSocket authentication");
    }

    @Test
    void malformedJwtIsRejectedBeforeAPrincipalIsCreated() {
        ChatWebSocketAuthenticationService service =
                new ChatWebSocketAuthenticationService(userDetailsService, jwtUtil, sessions);
        when(jwtUtil.extractEmail("malformed")).thenThrow(new IllegalArgumentException("bad token"));

        assertThatThrownBy(() -> service.authenticate("Bearer malformed"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid chat WebSocket authentication");
    }
}
