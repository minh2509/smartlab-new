package com.smartlab.controller;

import com.smartlab.dto.request.ChangeProjectLeaderRequest;
import com.smartlab.dto.request.ChangeProjectLeadersRequest;
import com.smartlab.dto.request.CreateProjectRequest;
import com.smartlab.dto.request.UpdateProjectRequest;
import com.smartlab.dto.request.UpdateProjectLeadershipRequest;
import com.smartlab.dto.response.LeaderCandidateResponse;
import com.smartlab.dto.response.ProjectResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/projects")
@Tag(name = "Projects", description = "Project core information and leader management.")
public class ProjectController {
    private final ProjectService projectService;

    @GetMapping
    @Operation(summary = "List visible projects")
    public List<ProjectResponse> list(
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return projectService.list(currentEmail);
    }

    @GetMapping("/public/recruiting")
    @Operation(summary = "List public projects currently recruiting members")
    public PublicPageResponse<ProjectResponse> listPublicRecruiting(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size
    ) {
        return projectService.listPublicRecruiting(page, size);
    }

    @GetMapping("/leader-candidates")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    @Operation(
            summary = "Search assignable project leader accounts",
            description = "Returns at most 20 active accounts without an inactive assigned role, ordered deterministically."
    )
    public List<LeaderCandidateResponse> findLeaderCandidates(
            @RequestParam(defaultValue = "") String query,
            @CurrentSecurityContext(expression = "authentication?.name") String adminEmail
    ) {
        return projectService.findLeaderCandidates(query, adminEmail);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one visible project")
    public ProjectResponse get(
            @PathVariable Long id,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return projectService.get(id, currentEmail);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PROJECT_MANAGE') and (hasRole('ADMIN') or hasRole('LEADER'))")
    @Operation(
            summary = "Create a project",
            description = "Administrators may choose leaders. A global LEADER is automatically assigned as the project's primary leader."
    )
    public ProjectResponse create(
            @Valid @RequestBody CreateProjectRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String adminEmail
    ) {
        return projectService.create(request, adminEmail);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Update project information",
            description = "The service authorizes an administrator or an active leader of this project."
    )
    public ProjectResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return projectService.update(id, request, currentEmail);
    }

    @PatchMapping("/{id}/leader")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    @Operation(
            summary = "Assign or change the primary project leader",
            description = "Assigns the first primary leader when none exists. On a change, the previous primary leader remains in the equal-permission leader set."
    )
    public ProjectResponse changeLeader(
            @PathVariable Long id,
            @Valid @RequestBody ChangeProjectLeaderRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String adminEmail
    ) {
        return projectService.changeLeader(id, request, adminEmail);
    }

    @PutMapping("/{id}/leaders")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    @Operation(
            summary = "Replace the complete project leader set",
            description = "Omitting the current primary leader clears the primary selection. An empty set removes every leader."
    )
    public ProjectResponse replaceLeaders(
            @PathVariable Long id,
            @Valid @RequestBody ChangeProjectLeadersRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String adminEmail
    ) {
        return projectService.replaceLeaders(id, request, adminEmail);
    }

    @PutMapping("/{id}/leadership")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    @Operation(
            summary = "Update the complete project leadership atomically",
            description = "Updates the complete active leader set and optional primary leader in one transaction. Removed leaders remain active project members."
    )
    public ProjectResponse updateLeadership(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectLeadershipRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String adminEmail
    ) {
        return projectService.updateLeadership(id, request, adminEmail);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    @Operation(summary = "Soft-delete a project")
    public void delete(
            @PathVariable Long id,
            @CurrentSecurityContext(expression = "authentication?.name") String adminEmail
    ) {
        projectService.delete(id, adminEmail);
    }
}
