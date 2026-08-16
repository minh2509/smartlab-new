package com.smartlab.service;

import com.smartlab.dto.request.ReplaceProjectResearchFieldsRequest;
import com.smartlab.dto.response.ProjectResearchFieldResponse;
import com.smartlab.dto.response.ProjectResponse;

import java.util.List;

public interface ProjectResearchFieldService {
    List<ProjectResearchFieldResponse> list(Long projectId, String currentEmail);

    List<ProjectResearchFieldResponse> replace(
            Long projectId,
            ReplaceProjectResearchFieldsRequest request,
            String currentEmail
    );

    List<ProjectResponse> filterVisibleProjects(Long researchFieldId, String currentEmail);
}
