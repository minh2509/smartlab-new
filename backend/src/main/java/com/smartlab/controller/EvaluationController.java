package com.smartlab.controller;

import com.smartlab.dto.request.CreateEvaluationRequest;
import com.smartlab.dto.request.UpdateEvaluationRequest;
import com.smartlab.dto.response.EvaluationResponse;
import com.smartlab.service.EvaluationService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Evaluations", description = "Member evaluation management (D4)")
public class EvaluationController {

    private final EvaluationService evaluationService;

    @PostMapping("/projects/{projectId}/evaluations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Create an evaluation for a project member",
            description = "Evaluator must be project LEADER or ADMIN. Evaluated user must be active in the project."
    )
    public EvaluationResponse create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateEvaluationRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return evaluationService.create(projectId, request, currentEmail);
    }

    @PatchMapping("/evaluations/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update an evaluation", description = "Can update the note and replace scores.")
    public EvaluationResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEvaluationRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return evaluationService.update(id, request, currentEmail);
    }

    @GetMapping("/me/evaluations")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my evaluations", description = "Returns all evaluations where the current user is the evaluated person.")
    public List<EvaluationResponse> myEvaluations(
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return evaluationService.getMyEvaluations(currentEmail);
    }
}
