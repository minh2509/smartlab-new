package com.smartlab.controller;

import com.smartlab.dto.response.AccountResponse;
import com.smartlab.dto.request.InvitationAcceptRequest;
import com.smartlab.dto.response.ErrorResponse;
import com.smartlab.service.AdminAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Invitations", description = "Public account activation flow for provisioned users.")
public class InvitationController {
    private final AdminAccountService adminAccountService;

    @PostMapping("/invitations/accept")
    @Operation(
            summary = "Accept account invitation",
            description = "Activates a provisioned account from an email invitation token and sets the user's password. This replaces public registration."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitation accepted and account activated",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invitation token is invalid, expired, or already accepted",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AccountResponse acceptInvite(@Valid @RequestBody InvitationAcceptRequest request) {
        return adminAccountService.acceptInvite(request);
    }
}
