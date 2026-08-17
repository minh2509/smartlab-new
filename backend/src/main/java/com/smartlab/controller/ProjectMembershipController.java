package com.smartlab.controller;

import com.smartlab.dto.response.ProjectMembershipHistoryResponse;
import com.smartlab.service.ProjectMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/memberships")
@PreAuthorize("isAuthenticated()")
@Tag(name = "My project memberships", description = "Current and removed project membership history.")
public class ProjectMembershipController {
    private final ProjectMemberService projectMemberService;

    @GetMapping("/me")
    @Operation(summary = "List the authenticated user's project membership history")
    public List<ProjectMembershipHistoryResponse> listMine(
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return projectMemberService.listMine(currentEmail);
    }
}
