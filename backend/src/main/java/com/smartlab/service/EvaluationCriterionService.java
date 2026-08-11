package com.smartlab.service;

import com.smartlab.dto.request.CreateEvaluationCriterionRequest;
import com.smartlab.dto.request.UpdateEvaluationCriterionRequest;
import com.smartlab.dto.response.EvaluationCriterionResponse;

import java.util.List;

public interface EvaluationCriterionService {

    List<EvaluationCriterionResponse> listByProject(Long projectId, String currentEmail);

    EvaluationCriterionResponse create(Long projectId, CreateEvaluationCriterionRequest request, String currentEmail);

    EvaluationCriterionResponse update(Long projectId, Long criterionId, UpdateEvaluationCriterionRequest request, String currentEmail);
}
