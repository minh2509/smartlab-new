package com.smartlab.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Optional refresh-token body for non-browser API clients.")
public class RefreshTokenRequest {
    @Schema(description = "Opaque refresh token returned by login or refresh")
    private String refreshToken;
}
