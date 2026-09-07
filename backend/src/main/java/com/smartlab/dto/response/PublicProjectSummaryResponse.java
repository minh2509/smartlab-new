package com.smartlab.dto.response;

import com.smartlab.enums.ProjectType;
import com.smartlab.enums.PublicProjectStatus;

import java.util.List;

public record PublicProjectSummaryResponse(
        Long id,
        String code,
        String name,
        String description,
        String goal,
        ProjectType projectType,
        PublicProjectStatus publicStatus,
        List<ProjectResearchFieldResponse> researchFields,
        List<PublicProjectLeaderResponse> leaders
) { }
