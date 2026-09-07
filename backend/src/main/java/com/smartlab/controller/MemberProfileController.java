package com.smartlab.controller;

import com.smartlab.dto.request.AdminUpdateMemberProfileRequest;
import com.smartlab.config.OpenApiConfig;
import com.smartlab.dto.request.UpdateMemberProfileRequest;
import com.smartlab.dto.response.MemberProfileResponse;
import com.smartlab.service.MemberProfileService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Member Profiles", description = "Authenticated member profile and internal member management.")
public class MemberProfileController {
    private final MemberProfileService memberProfileService;

    @GetMapping("/me/profile")
    @PreAuthorize("hasAuthority('PROFILE_READ')")
    @Operation(summary = "Get my member profile")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public MemberProfileResponse getOwnProfile(java.security.Principal principal) {
        return memberProfileService.getOwnProfile(principal.getName());
    }

    @PatchMapping("/me/profile")
    @PreAuthorize("hasAuthority('PROFILE_UPDATE')")
    @Operation(summary = "Update my member profile")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public MemberProfileResponse updateOwnProfile(
            java.security.Principal principal,
            @Valid @RequestBody UpdateMemberProfileRequest request
    ) {
        return memberProfileService.updateOwnProfile(principal.getName(), request);
    }

    @GetMapping("/members")
    @Operation(summary = "List member profiles for authenticated workspace use")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public List<MemberProfileResponse> listMembers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String fieldCode,
            @RequestParam(required = false) String status
    ) {
        return memberProfileService.listMembers(keyword, fieldCode, status);
    }

    @GetMapping("/admin/members")
    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    @Operation(summary = "List all member profiles for administration")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public List<MemberProfileResponse> listAllMembers() {
        return memberProfileService.listAllMembers();
    }

    @PatchMapping("/admin/members/{userId}")
    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    @Operation(summary = "Update a member profile as administrator")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public MemberProfileResponse updateMember(
            @PathVariable String userId,
            @Valid @RequestBody AdminUpdateMemberProfileRequest request
    ) {
        return memberProfileService.updateMember(userId, request);
    }
}
