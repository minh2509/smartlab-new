package com.smartlab.controller;

import com.smartlab.dto.request.ReplaceProjectResearchFieldsRequest;
import com.smartlab.dto.response.ProjectResearchFieldResponse;
import com.smartlab.dto.response.ProjectResponse;
import com.smartlab.service.ProjectResearchFieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/projects")
@Tag(name = "Project research fields", description = "Project-scoped research field assignments.")
public class ProjectResearchFieldController {
    private final ProjectResearchFieldService projectResearchFieldService;

    @GetMapping(params = "researchFieldId")
    @Operation(summary = "List visible projects in one active research field")
    public List<ProjectResponse> filterProjects(
            @RequestParam Long researchFieldId,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return projectResearchFieldService.filterVisibleProjects(researchFieldId, currentEmail);
    }

    @GetMapping("/{projectId}/research-fields")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List a project's research fields")
    public List<ProjectResearchFieldResponse> list(
            @PathVariable Long projectId,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return projectResearchFieldService.list(projectId, currentEmail);
    }

    @PatchMapping("/{projectId}/research-fields")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Replace a project's complete research field set")
    public List<ProjectResearchFieldResponse> replace(
            @PathVariable Long projectId,
            @Valid @RequestBody ReplaceProjectResearchFieldsRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return projectResearchFieldService.replace(projectId, request, currentEmail);
    }
}
