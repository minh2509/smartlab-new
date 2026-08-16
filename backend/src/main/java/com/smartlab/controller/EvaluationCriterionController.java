package com.smartlab.controller;

import com.smartlab.dto.request.CreateEvaluationCriterionRequest;
import com.smartlab.dto.request.UpdateEvaluationCriterionRequest;
import com.smartlab.dto.response.EvaluationCriterionResponse;
import com.smartlab.service.EvaluationCriterionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/evaluation-criteria")
@Tag(name = "Evaluation Criteria", description = "Dynamic evaluation criteria management per project (D4)")
public class EvaluationCriterionController {

    private final EvaluationCriterionService criterionService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List evaluation criteria for a project")
    public List<EvaluationCriterionResponse> list(
            @PathVariable Long projectId,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return criterionService.listByProject(projectId, currentEmail);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create an evaluation criterion", description = "Requires project LEADER or ADMIN.")
    public EvaluationCriterionResponse create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateEvaluationCriterionRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return criterionService.create(projectId, request, currentEmail);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update an evaluation criterion", description = "Requires project LEADER or ADMIN. Use isActive=false to deactivate.")
    public EvaluationCriterionResponse update(
            @PathVariable Long projectId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateEvaluationCriterionRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return criterionService.update(projectId, id, request, currentEmail);
    }
}
