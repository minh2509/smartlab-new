package com.smartlab.controller;

import com.smartlab.config.OpenApiConfig;
import com.smartlab.dto.request.AccountProvisionRequest;
import com.smartlab.dto.request.BulkAccountInvitationRequest;
import com.smartlab.dto.response.AccountResponse;
import com.smartlab.dto.request.AssignRolesRequest;
import com.smartlab.dto.response.ErrorResponse;
import com.smartlab.dto.response.InvitationResponse;
import com.smartlab.dto.response.BulkAccountInvitationBatchResponse;
import com.smartlab.dto.response.BulkAccountInvitationPreviewResponse;
import com.smartlab.dto.request.PermissionOverrideRequest;
import com.smartlab.dto.response.PageResponse;
import com.smartlab.service.AdminAccountService;
import com.smartlab.service.BulkAccountInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/accounts")
@Tag(name = "Admin Accounts", description = "Admin-only account provisioning, role assignment, user activation, and user-level permission overrides.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminAccountController {
    private final AdminAccountService adminAccountService;
    private final BulkAccountInvitationService bulkAccountInvitationService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(
            summary = "List lab accounts",
            description = "Returns paginated lab accounts so admins can manage members from a table. Required permission: USER_MANAGE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Accounts returned",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing USER_MANAGE permission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PageResponse<AccountResponse> listAccounts(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, capped at 50", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        return adminAccountService.listAccounts(page, size);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(
            summary = "Provision a member account",
            description = "Creates a user account without public registration and sends an email invitation link so the user can set their own password. Required permission: USER_MANAGE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account provisioned and invitation created",
                    content = @Content(schema = @Schema(implementation = InvitationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing USER_MANAGE permission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public InvitationResponse provision(
            @Valid @RequestBody AccountProvisionRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String adminEmail
    ) {
        return adminAccountService.provision(request, adminEmail);
    }

    @PostMapping("/invitations/resend")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(
            summary = "Resend account invitation",
            description = "Updates the existing invitation record with a new token and expiration time, then sends a new email invitation link. Required permission: USER_MANAGE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitation token regenerated",
                    content = @Content(schema = @Schema(implementation = InvitationResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing USER_MANAGE permission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public InvitationResponse resendInvite(
            @Parameter(description = "Email of the provisioned account", example = "member@smartlab.local")
            @RequestParam String email,
            @CurrentSecurityContext(expression = "authentication?.name") String adminEmail
    ) {
        return adminAccountService.resendInvite(email, adminEmail);
    }

    @PostMapping("/invitation-batches/preview")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Preview bulk account invitations",
            description = "Validates up to 100 name/email rows and selected active roles without creating accounts or sending email.")
    public BulkAccountInvitationPreviewResponse previewBulkInvitations(
            @Valid @RequestBody BulkAccountInvitationRequest request
    ) {
        return bulkAccountInvitationService.preview(request);
    }

    @PostMapping("/invitation-batches")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Provision bulk account invitations",
            description = "Creates inactive SmartLab accounts and queues invitation email after validation. Mail delivery is processed from the database outbox.")
    @ApiResponse(responseCode = "202", description = "Batch accepted and queued")
    public org.springframework.http.ResponseEntity<BulkAccountInvitationBatchResponse> provisionBulkInvitations(
            @Valid @RequestBody BulkAccountInvitationRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String adminEmail
    ) {
        return org.springframework.http.ResponseEntity.accepted()
                .body(bulkAccountInvitationService.provision(request, adminEmail));
    }

    @GetMapping("/invitation-batches/{batchId}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Read bulk invitation batch outcomes")
    public BulkAccountInvitationBatchResponse getBulkInvitationBatch(@PathVariable String batchId) {
        return bulkAccountInvitationService.getBatch(batchId);
    }

    @PostMapping("/invitation-batches/{batchId}/items/{itemId}/resend")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Queue a new invitation email for one bulk item")
    @ApiResponse(responseCode = "202", description = "Resend accepted and queued")
    public org.springframework.http.ResponseEntity<Void> resendBulkInvitation(
            @PathVariable String batchId,
            @PathVariable String itemId,
            @CurrentSecurityContext(expression = "authentication?.name") String adminEmail
    ) {
        bulkAccountInvitationService.resend(batchId, itemId, adminEmail);
        return org.springframework.http.ResponseEntity.accepted().build();
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(
            summary = "Replace user roles",
            description = "Replaces all roles assigned to a user. Changing roles revokes existing sessions so the next login gets fresh permissions. Required permission: ROLE_MANAGE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User roles updated",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "One or more role codes are invalid",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing ROLE_MANAGE permission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AccountResponse updateRoles(
            @Parameter(description = "Public user id", example = "d3151812-a9d9-4244-bd51-6c62d2f11c92")
            @PathVariable String userId,
            @Valid @RequestBody AssignRolesRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String adminEmail
    ) {
        return adminAccountService.updateRoles(userId, request.getRoleCodes(), adminEmail);
    }

    @PatchMapping("/{userId}/active")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(
            summary = "Activate or deactivate account",
            description = "Toggles account login ability. Deactivating a user revokes all active sessions. Required permission: USER_MANAGE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User active status updated",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing USER_MANAGE permission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AccountResponse setActive(
            @Parameter(description = "Public user id", example = "d3151812-a9d9-4244-bd51-6c62d2f11c92")
            @PathVariable String userId,
            @Parameter(description = "true to activate, false to block login", example = "false")
            @RequestParam boolean active
    ) {
        return adminAccountService.setActive(userId, active);
    }

    @PutMapping("/{userId}/permissions/{permissionCode}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE')")
    @Operation(
            summary = "Set user permission override",
            description = "Grants or denies one permission directly for a user, on top of role permissions. Updating overrides revokes old sessions. Required permission: PERMISSION_MANAGE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User permission override saved",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "404", description = "Permission or user not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing PERMISSION_MANAGE permission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AccountResponse setPermissionOverride(
            @Parameter(description = "Public user id", example = "d3151812-a9d9-4244-bd51-6c62d2f11c92")
            @PathVariable String userId,
            @Parameter(description = "Permission code", example = "PROJECT_MANAGE")
            @PathVariable String permissionCode,
            @Valid @RequestBody PermissionOverrideRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String adminEmail
    ) {
        return adminAccountService.setPermissionOverride(userId, permissionCode, request, adminEmail);
    }

    @DeleteMapping("/{userId}/permissions/{permissionCode}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE')")
    @Operation(
            summary = "Remove user permission override",
            description = "Removes a direct user-level grant/deny so effective permissions fall back to role permissions. Required permission: PERMISSION_MANAGE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permission override removed",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing PERMISSION_MANAGE permission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AccountResponse removePermissionOverride(
            @Parameter(description = "Public user id", example = "d3151812-a9d9-4244-bd51-6c62d2f11c92")
            @PathVariable String userId,
            @Parameter(description = "Permission code", example = "PROJECT_MANAGE")
            @PathVariable String permissionCode
    ) {
        return adminAccountService.removePermissionOverride(userId, permissionCode);
    }
}
