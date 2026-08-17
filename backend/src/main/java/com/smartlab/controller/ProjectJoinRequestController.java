package com.smartlab.controller;

import com.smartlab.dto.request.CreateProjectJoinRequest;
import com.smartlab.dto.request.ReviewProjectJoinRequest;
import com.smartlab.dto.response.ProjectJoinRequestResponse;
import com.smartlab.enums.ProjectJoinRequestStatus;
import com.smartlab.service.ProjectJoinRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/join-requests")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Project join requests", description = "Request and review project membership.")
public class ProjectJoinRequestController {
    private final ProjectJoinRequestService projectJoinRequestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Request to join a visible project")
    public ProjectJoinRequestResponse create(
            @PathVariable Long projectId,
            @Valid @RequestBody(required = false) CreateProjectJoinRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return projectJoinRequestService.create(projectId, request, currentEmail);
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current user's latest request for a project")
    public ResponseEntity<ProjectJoinRequestResponse> getLatestMine(
            @PathVariable Long projectId,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return projectJoinRequestService.getLatestMine(projectId, currentEmail)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancel the current user's pending request")
    public void cancelMine(
            @PathVariable Long projectId,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        projectJoinRequestService.cancelMine(projectId, currentEmail);
    }

    @GetMapping
    @Operation(summary = "List project join requests for a project manager")
    public List<ProjectJoinRequestResponse> listForManagement(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "PENDING") ProjectJoinRequestStatus status,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return projectJoinRequestService.listForManagement(projectId, status, currentEmail);
    }

    @PatchMapping("/{requestId}")
    @Operation(summary = "Approve or reject a pending project join request")
    public ProjectJoinRequestResponse review(
            @PathVariable Long projectId,
            @PathVariable Long requestId,
            @Valid @RequestBody ReviewProjectJoinRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return projectJoinRequestService.review(projectId, requestId, request, currentEmail);
    }
}
