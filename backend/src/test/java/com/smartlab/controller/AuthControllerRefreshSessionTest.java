package com.smartlab.controller;

import com.smartlab.dto.request.AuthRequest;
import com.smartlab.dto.request.RefreshTokenRequest;
import com.smartlab.dto.response.AuthResponse;
import com.smartlab.entity.UserEntity;
import com.smartlab.entity.UserSessionEntity;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.PasswordResetService;
import com.smartlab.service.UserSessionService;
import com.smartlab.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerRefreshSessionTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private AppUserDetailService appUserDetailService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private HttpServletRequest httpRequest;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(
                authenticationManager,
                appUserDetailService,
                jwtUtil,
                passwordResetService,
                userRepository,
                userSessionService
        );
    }

    @Test
    void refreshTokenRequestToStringDoesNotExposeRawCredential() {
        RefreshTokenRequest request =
                new RefreshTokenRequest("super-secret-refresh-token");

        assertThat(request.toString())
                .doesNotContain("super-secret-refresh-token");
    }

    @Test
    void loginReturnsAccessAndRefreshCredentialsAndSetsBothCookies() {
        UserEntity user = persistedUser();
        UserDetails details = enabledDetails(user);
        UserSessionEntity session = activeSession(user, "session-login");

        when(appUserDetailService.loadUserByUsername(user.getEmail())).thenReturn(details);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(userSessionService.createSessionCredential(user, "JUnit", "127.0.0.1"))
                .thenReturn(new UserSessionService.SessionCredential(session, "refresh-login"));
        when(jwtUtil.generateToken(details, session.getSessionId()))
                .thenReturn("access-login");

        ResponseEntity<?> response = controller.login(
                AuthRequest.builder()
                        .email(user.getEmail())
                        .password("password")
                        .build(),
                httpRequest
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(AuthResponse.class);

        AuthResponse body = (AuthResponse) response.getBody();
        assertThat(body.getEmail()).isEqualTo(user.getEmail());
        assertThat(body.getToken()).isEqualTo("access-login");
        assertThat(body.getRefreshToken()).isEqualTo("refresh-login");
        assertThat(body.getSessionId()).isEqualTo("session-login");

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull();
        assertThat(cookies).hasSize(2);
        assertThat(cookies).anyMatch(value ->
                value.startsWith("jwt=access-login") && value.contains("HttpOnly"));
        assertThat(cookies).anyMatch(value ->
                value.startsWith("refresh_token=refresh-login") && value.contains("HttpOnly"));

        verify(authenticationManager).authenticate(any());
    }

    @Test
    void disabledAuthenticationDoesNotCreateSession() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new DisabledException("disabled"));

        ResponseEntity<?> response = controller.login(
                AuthRequest.builder()
                        .email("disabled@example.test")
                        .password("password")
                        .build(),
                httpRequest
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(userSessionService, never()).createSessionCredential(any(), any(), any());
        verify(jwtUtil, never()).generateToken(any(), any());
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void refreshCookieRotatesRefreshTokenAndKeepsSameSession() {
        UserEntity user = persistedUser();
        UserDetails details = enabledDetails(user);
        UserSessionEntity session = activeSession(user, "session-refresh");

        when(httpRequest.getCookies())
                .thenReturn(new Cookie[]{new Cookie("refresh_token", "refresh-old")});

        when(userSessionService.rotateRefreshToken("refresh-old"))
                .thenReturn(new UserSessionService.RefreshCredential(
                        session,
                        "refresh-new",
                        details
                ));

        when(jwtUtil.generateToken(details, "session-refresh"))
                .thenReturn("access-new");

        ResponseEntity<?> response = controller.refresh(null, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse body = (AuthResponse) response.getBody();

        assertThat(body.getToken()).isEqualTo("access-new");
        assertThat(body.getRefreshToken()).isEqualTo("refresh-new");
        assertThat(body.getSessionId()).isEqualTo("session-refresh");

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(2);
        assertThat(cookies).anyMatch(value -> value.startsWith("jwt=access-new"));
        assertThat(cookies).anyMatch(value -> value.startsWith("refresh_token=refresh-new"));
    }

    @Test
    void invalidRefreshReturnsUnauthorizedAndClearsBothCookies() {
        when(httpRequest.getCookies())
                .thenReturn(new Cookie[]{new Cookie("refresh_token", "invalid")});

        when(userSessionService.rotateRefreshToken("invalid"))
                .thenThrow(new RuntimeException("invalid"));

        ResponseEntity<?> response = controller.refresh(null, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(2);
        assertThat(cookies).allMatch(value -> value.contains("Max-Age=0"));
    }

    @Test
    void logoutFallsBackToRefreshWhenAccessJwtCannotBeParsed() {
        when(httpRequest.getHeader("Authorization"))
                .thenReturn("Bearer expired-access");
        when(jwtUtil.extractSessionId("expired-access"))
                .thenThrow(new RuntimeException("expired"));
        when(httpRequest.getCookies())
                .thenReturn(new Cookie[]{new Cookie("refresh_token", "refresh-current")});

        ResponseEntity<?> response = controller.logout(null, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Logout successful");

        verify(userSessionService).revokeByRefreshToken("refresh-current");

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(2);
        assertThat(cookies).allMatch(value -> value.contains("Max-Age=0"));
    }

    @Test
    void logoutWithAccessSessionDoesNotRevokeDifferentRefreshCredential() {
        when(httpRequest.getHeader("Authorization"))
                .thenReturn("Bearer access-current");
        when(jwtUtil.extractSessionId("access-current"))
                .thenReturn("session-current");

        ResponseEntity<?> response = controller.logout(null, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userSessionService).revokeSession("session-current");
        verify(userSessionService, never()).revokeByRefreshToken(any());
    }

    private UserEntity persistedUser() {
        return UserEntity.builder()
                .id(1L)
                .userId("member-1")
                .name("Member")
                .email("member@example.test")
                .password("encoded")
                .isActive(true)
                .isAccountVerified(true)
                .build();
    }

    private UserDetails enabledDetails(UserEntity user) {
        return User.withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities("PROFILE_READ")
                .build();
    }

    private UserSessionEntity activeSession(UserEntity user, String sessionId) {
        return UserSessionEntity.builder()
                .id(10L)
                .sessionId(sessionId)
                .user(user)
                .refreshTokenHash("a".repeat(64))
                .expiresAt(Instant.now().plusSeconds(3600))
                .lastSeenAt(Instant.now())
                .build();
    }
}
