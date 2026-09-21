package com.smartlab.dto.response;

import com.smartlab.enums.ProjectType;
import com.smartlab.enums.PublicProjectStatus;

import java.time.LocalDate;
import java.util.List;

public record PublicProjectSummaryResponse(
        Long id,
        String code,
        String name,
        String description,
        String goal,
        ProjectType projectType,
        PublicProjectStatus publicStatus,
        LocalDate startDate,
        String coverUrl,
        List<ProjectResearchFieldResponse> researchFields,
        List<PublicProjectLeaderResponse> leaders
) { }

