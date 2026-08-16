package com.smartlab.controller;

import com.smartlab.config.OpenApiConfig;
import com.smartlab.entity.UserEntity;
import com.smartlab.dto.request.AuthRequest;
import com.smartlab.dto.request.RefreshTokenRequest;
import com.smartlab.dto.response.AuthResponse;
import com.smartlab.dto.request.ResetPasswordRequest;
import com.smartlab.dto.request.VerifyResetOtpRequest;
import com.smartlab.dto.response.ErrorResponse;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.PasswordResetService;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.UserSessionService;
import com.smartlab.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, logout, session check, and forgot password flow.")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final AppUserDetailService appUserDetailService;
    private final JwtUtil jwtUtil;
    private final PasswordResetService passwordResetService;
    private final UserRepository userRepository;
    private final UserSessionService userSessionService;

    @Value("${smartlab.security.cookie-secure:false}")
    private boolean secureCookies;

    @PostMapping("/login")
    @Operation(
            summary = "Login and create a session",
            description = "Authenticates a provisioned SmartLab account, creates a server-side session, returns a JWT, and also sets an httpOnly jwt cookie."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Email or password is incorrect",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Account is inactive, role is inactive, or authentication failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> login(@RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        try {
            authenticate(request.getEmail(), request.getPassword());
            final UserDetails userDetails = appUserDetailService.loadUserByUsername(request.getEmail());
            UserEntity user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + request.getEmail()));
            var credential = userSessionService.createSessionCredential(
                    user,
                    httpRequest.getHeader("User-Agent"),
                    getClientIp(httpRequest)
            );
            final String jwtToken = jwtUtil.generateToken(userDetails, credential.session().getSessionId());

            return authenticationResponse(
                    request.getEmail(),
                    jwtToken,
                    credential.session().getSessionId(),
                    credential.refreshToken(),
                    credential.session().getExpiresAt()
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("Incorrect email or password"));
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("User or assigned role is disabled"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("Authentication Failed"));
        }
    }

    private void authenticate(String email, String password) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
    }

    @GetMapping("/is-authenticated")
    @Operation(
            summary = "Check current authentication",
            description = "Returns true when the current JWT is valid and the server-side session is still active.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication status returned",
                    content = @Content(schema = @Schema(implementation = Boolean.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired, revoked, or invalid token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Boolean> isAuthenticated(@CurrentSecurityContext(expression = "authentication?.name") String email) {
        return ResponseEntity.ok(email != null);
    }

    @PostMapping("/send-reset-otp")
    @Operation(
            summary = "Send forgot-password OTP",
            description = "Sends a short-lived OTP to a provisioned account email. This endpoint is public so users can reset forgotten passwords."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reset OTP sent"),
            @ApiResponse(responseCode = "404", description = "Account email does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Email could not be sent",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> sendResetOtp(
            @Parameter(description = "Provisioned account email", example = "member@smartlab.local")
            @RequestParam String email
    ) {
        try {
            passwordResetService.sendResetOtp(email);
            return ResponseEntity.ok().build();
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("Account email does not exist"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(e.getMessage()));
        }
    }

    @PostMapping("/verify-reset-otp")
    @Operation(
            summary = "Verify forgot-password OTP",
            description = "Checks whether the email exists and the OTP is valid before allowing the user to enter a new password."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reset OTP is valid"),
            @ApiResponse(responseCode = "400", description = "OTP invalid or expired",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account email does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> verifyResetOtp(@Valid @RequestBody VerifyResetOtpRequest request) {
        try {
            passwordResetService.verifyResetOtp(request.getEmail(), request.getOtp());
            return ResponseEntity.ok().build();
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("Account email does not exist"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error(e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    @Operation(
            summary = "Reset password by OTP",
            description = "Updates the user's password with a valid reset OTP, then revokes all old sessions for that account."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset successful and old sessions revoked"),
            @ApiResponse(responseCode = "400", description = "OTP invalid/expired or reset failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account email does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        try {
            passwordResetService.resetPassword(
                    resetPasswordRequest.getEmail(),
                    resetPasswordRequest.getOtp(),
                    resetPasswordRequest.getNewPassword()
            );
            userSessionService.revokeAllByEmail(resetPasswordRequest.getEmail());
            return ResponseEntity.ok().build();
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("Account email does not exist"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error(e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Rotate refresh token and issue a new access JWT",
            description = "Uses the current refresh credential without requiring a valid access JWT. Rotation keeps the same server-side session and absolute session expiry."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refresh successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh token is missing, invalid, expired, revoked, rotated, or belongs to an unusable account",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest
    ) {
        String refreshToken = resolveRefreshToken(request, httpRequest);
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("Invalid refresh token"));
        }

        try {
            var credential = userSessionService.rotateRefreshToken(refreshToken);
            String jwtToken = jwtUtil.generateToken(
                    credential.userDetails(),
                    credential.session().getSessionId()
            );

            return authenticationResponse(
                    credential.userDetails().getUsername(),
                    jwtToken,
                    credential.session().getSessionId(),
                    credential.refreshToken(),
                    credential.session().getExpiresAt()
            );
        } catch (Exception ignored) {
            return clearAuthenticationCookies(
                    ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(error("Invalid refresh token"))
            );
        }
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Logout current session",
            description = "Revokes the current server-side session. A parseable access JWT is authoritative; if it is unavailable or expired, the refresh credential is used as fallback. Both authentication cookies are always cleared."
    )
    @ApiResponse(responseCode = "200", description = "Logout successful",
            content = @Content(schema = @Schema(implementation = String.class)))
    public ResponseEntity<?> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest
    ) {
        boolean resolvedByAccessToken = false;
        String token = resolveToken(httpRequest);

        if (token != null) {
            try {
                userSessionService.revokeSession(jwtUtil.extractSessionId(token));
                resolvedByAccessToken = true;
            } catch (Exception ignored) {
            }
        }

        if (!resolvedByAccessToken) {
            String refreshToken = resolveRefreshToken(request, httpRequest);
            if (refreshToken != null) {
                try {
                    userSessionService.revokeByRefreshToken(refreshToken);
                } catch (Exception ignored) {
                }
            }
        }

        return clearAuthenticationCookies(ResponseEntity.ok("Logout successful"));
    }

    private ResponseEntity<AuthResponse> authenticationResponse(
            String email,
            String jwtToken,
            String sessionId,
            String refreshToken,
            Instant sessionExpiresAt
    ) {
        ResponseCookie jwtCookie = ResponseCookie.from("jwt", jwtToken)
                .httpOnly(true)
                .secure(secureCookies)
                .maxAge(Duration.ofHours(10))
                .path("/")
                .sameSite("Strict")
                .build();

        Duration refreshMaxAge = Duration.between(Instant.now(), sessionExpiresAt);
        if (refreshMaxAge.isNegative()) {
            refreshMaxAge = Duration.ZERO;
        }

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(secureCookies)
                .maxAge(refreshMaxAge)
                .path("/")
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(AuthResponse.builder()
                        .email(email)
                        .token(jwtToken)
                        .sessionId(sessionId)
                        .refreshToken(refreshToken)
                        .build());
    }

    private String resolveRefreshToken(
            RefreshTokenRequest request,
            HttpServletRequest httpRequest
    ) {
        if (httpRequest.getCookies() != null) {
            for (var cookie : httpRequest.getCookies()) {
                if ("refresh_token".equals(cookie.getName())
                        && cookie.getValue() != null
                        && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }

        if (request != null
                && request.getRefreshToken() != null
                && !request.getRefreshToken().isBlank()) {
            return request.getRefreshToken();
        }

        return null;
    }

    private <T> ResponseEntity<T> clearAuthenticationCookies(ResponseEntity<T> response) {
        ResponseCookie jwtCookie = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(secureCookies)
                .maxAge(0)
                .path("/")
                .sameSite("Strict")
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(secureCookies)
                .maxAge(0)
                .path("/")
                .sameSite("Strict")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.putAll(response.getHeaders());
        headers.add(HttpHeaders.SET_COOKIE, jwtCookie.toString());
        headers.add(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return new ResponseEntity<>(response.getBody(), headers, response.getStatusCode());
    }

    private String resolveToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        if (request.getCookies() == null) {
            return null;
        }
        for (var cookie : request.getCookies()) {
            if ("jwt".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", true);
        error.put("message", message);
        return error;
    }
}
