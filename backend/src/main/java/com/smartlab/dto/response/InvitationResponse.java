package com.smartlab.dto.response;

import com.smartlab.enums.InvitationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@Schema(description = "Invitation delivery result. Raw tokens are sent by email and are not exposed in this response.")
public class InvitationResponse {
    @Schema(description = "Invited account email", example = "member@smartlab.local")
    private String email;

    @Schema(description = "Current invitation status", example = "PENDING")
    private InvitationStatus status;

    @Schema(description = "Invitation expiry time")
    private Instant expiresAt;

    @Schema(description = "Email delivery target", example = "member@smartlab.local")
    private String sentTo;
}
