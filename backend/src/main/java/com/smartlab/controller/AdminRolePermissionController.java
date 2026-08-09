package com.smartlab.controller;

import com.smartlab.config.OpenApiConfig;
import com.smartlab.entity.PermissionEntity;
import com.smartlab.entity.RoleEntity;
import com.smartlab.dto.request.PermissionRequest;
import com.smartlab.dto.request.RolePermissionRequest;
import com.smartlab.dto.request.RoleRequest;
import com.smartlab.dto.response.ErrorResponse;
import com.smartlab.service.AdminRolePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
@Tag(name = "Admin RBAC", description = "Admin-only role and permission management.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminRolePermissionController {
    private final AdminRolePermissionService adminRolePermissionService;

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(
            summary = "List roles",
            description = "Returns all system and custom roles. Required permission: ROLE_MANAGE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roles returned",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = RoleEntity.class)))),
            @ApiResponse(responseCode = "403", description = "Missing ROLE_MANAGE permission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<RoleEntity> getRoles() {
        return adminRolePermissionService.getRoles();
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(
            summary = "Create custom role",
            description = "Creates a new role for future scaling. Default system roles are ADMIN, LEADER, and MEMBER. Required permission: ROLE_MANAGE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role created",
                    content = @Content(schema = @Schema(implementation = RoleEntity.class))),
            @ApiResponse(responseCode = "409", description = "Role code already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing ROLE_MANAGE permission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public RoleEntity createRole(@Valid @RequestBody RoleRequest request) {
        return adminRolePermissionService.createRole(request);
    }

    @PutMapping("/roles/{code}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(
            summary = "Update role",
            description = "Updates role name, description, and active status. If a role is inactive, users owning that role cannot log in. Required permission: ROLE_MANAGE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role updated",
                    content = @Content(schema = @Schema(implementation = RoleEntity.class))),
            @ApiResponse(responseCode = "404", description = "Role not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing ROLE_MANAGE permission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public RoleEntity updateRole(
            @Parameter(description = "Role code", example = "LEADER")
            @PathVariable String code,
            @Valid @RequestBody RoleRequest request
    ) {
        return adminRolePermissionService.updateRole(code, request);
    }

    @PutMapping("/roles/{code}/permissions")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE')")
    @Operation(
            summary = "Replace permissions of a role",
            description = "Sets the full permission list for a role. When new permissions are added by backend, admin can attach them here. Required permission: PERMISSION_MANAGE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role permissions updated",
                    content = @Content(schema = @Schema(implementation = RoleEntity.class))),
            @ApiResponse(responseCode = "400", description = "One or more permission codes are invalid",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing PERMISSION_MANAGE permission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public RoleEntity setRolePermissions(
            @Parameter(description = "Role code", example = "LEADER")
            @PathVariable String code,
            @Valid @RequestBody RolePermissionRequest request
    ) {
        return adminRolePermissionService.setRolePermissions(code, request.getPermissionCodes());
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE')")
    @Operation(
            summary = "List permissions",
            description = "Returns all backend-defined permissions that can be assigned to roles or overridden per user. Required permission: PERMISSION_MANAGE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permissions returned",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PermissionEntity.class)))),
            @ApiResponse(responseCode = "403", description = "Missing PERMISSION_MANAGE permission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<PermissionEntity> getPermissions() {
        return adminRolePermissionService.getPermissions();
    }

    @PostMapping("/permissions")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE')")
    @Operation(
            summary = "Create permission",
            description = "Creates a permission code that backend endpoints can later check with @PreAuthorize. Required permission: PERMISSION_MANAGE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permission created",
                    content = @Content(schema = @Schema(implementation = PermissionEntity.class))),
            @ApiResponse(responseCode = "409", description = "Permission code already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing PERMISSION_MANAGE permission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PermissionEntity createPermission(@Valid @RequestBody PermissionRequest request) {
        return adminRolePermissionService.createPermission(request);
    }

    @PutMapping("/permissions/{code}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE')")
    @Operation(
            summary = "Update permission",
            description = "Updates permission metadata and active status. Inactive permissions are ignored when calculating effective permissions. Required permission: PERMISSION_MANAGE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permission updated",
                    content = @Content(schema = @Schema(implementation = PermissionEntity.class))),
            @ApiResponse(responseCode = "404", description = "Permission not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing PERMISSION_MANAGE permission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PermissionEntity updatePermission(
            @Parameter(description = "Permission code", example = "PROJECT_MANAGE")
            @PathVariable String code,
            @Valid @RequestBody PermissionRequest request
    ) {
        return adminRolePermissionService.updatePermission(code, request);
    }
}
