package com.smartlab.controller;

import com.smartlab.dto.request.AddProjectMemberRequest;
import com.smartlab.dto.response.ProjectMemberCandidateResponse;
import com.smartlab.dto.response.ProjectMemberResponse;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.service.ProjectMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/{projectId}")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Project members", description = "Project-scoped membership with retained removal history.")
public class ProjectMemberController {
    private final ProjectMemberService projectMemberService;

    @GetMapping("/members")
    @Operation(summary = "List active or removed project members")
    public List<ProjectMemberResponse> list(
            @PathVariable Long projectId,
            @RequestParam(required = false) ProjectMemberStatus status,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return projectMemberService.list(projectId, status, currentEmail);
    }

    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add or reactivate a project member")
    public ProjectMemberResponse add(
            @PathVariable Long projectId,
            @Valid @RequestBody AddProjectMemberRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return projectMemberService.add(projectId, request, currentEmail);
    }

    @DeleteMapping("/members/{memberUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark a project member as removed")
    public void remove(
            @PathVariable Long projectId,
            @PathVariable @Size(max = 36) String memberUserId,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        projectMemberService.remove(projectId, memberUserId, currentEmail);
    }

    @GetMapping("/member-candidates")
    @Operation(summary = "Search active accounts that are not active project members")
    public List<ProjectMemberCandidateResponse> findCandidates(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "") String query,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return projectMemberService.findCandidates(projectId, query, currentEmail);
    }
}
