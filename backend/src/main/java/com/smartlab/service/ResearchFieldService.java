package com.smartlab.service;

import com.smartlab.dto.request.CreateResearchFieldRequest;
import com.smartlab.dto.request.UpdateResearchFieldRequest;
import com.smartlab.dto.response.ResearchFieldResponse;

import java.util.List;

public interface ResearchFieldService {
    List<ResearchFieldResponse> listActive();

    ResearchFieldResponse getActiveByCode(String code);

    List<ResearchFieldResponse> listAll();

    ResearchFieldResponse create(CreateResearchFieldRequest request);

    ResearchFieldResponse update(Long id, UpdateResearchFieldRequest request);

    void delete(Long id);
}
