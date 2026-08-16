package com.smartlab.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "Authentication response containing JWT and server-side session id.")
public class AuthResponse {
    @Schema(description = "Authenticated email", example = "admin@smartlab.local")
    private String email;

    @Schema(description = "JWT access token for Authorization: Bearer <token>", example = "eyJhbGciOiJIUzUxMiJ9...")
    private String token;

    @Schema(description = "Server-side session id embedded in the JWT", example = "8f239d28-d16e-4715-9061-0ef24cf50686")
    private String sessionId;

    @Schema(description = "Opaque refresh token. The server stores only its SHA-256 hash.")
    private String refreshToken;
}
