package com.smartlab.controller;

import com.smartlab.config.OpenApiConfig;
import com.smartlab.entity.UserEntity;
import com.smartlab.dto.response.AccountResponse;
import com.smartlab.dto.response.ErrorResponse;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.AdminAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Current authenticated user profile and effective RBAC view.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ProfileController {
    private final UserRepository userRepository;
    private final AdminAccountService adminAccountService;

    @GetMapping("/profile")
    @Operation(
            summary = "Get current user profile",
            description = "Returns the current user's account status, assigned roles, and effective permissions after role permissions and user overrides are applied."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current profile returned",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired, revoked, or invalid token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AccountResponse getProfile(@CurrentSecurityContext(expression = "authentication?.name") String username) {
        UserEntity user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return adminAccountService.toResponse(user);
    }
}
