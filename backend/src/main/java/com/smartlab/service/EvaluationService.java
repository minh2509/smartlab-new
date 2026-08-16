package com.smartlab.service;

import com.smartlab.dto.request.CreateEvaluationRequest;
import com.smartlab.dto.request.UpdateEvaluationRequest;
import com.smartlab.dto.response.EvaluationResponse;

import java.util.List;

public interface EvaluationService {

    EvaluationResponse create(Long projectId, CreateEvaluationRequest request, String currentEmail);

    EvaluationResponse update(Long evaluationId, UpdateEvaluationRequest request, String currentEmail);

    List<EvaluationResponse> getMyEvaluations(String currentEmail);
}
