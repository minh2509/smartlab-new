package com.smartlab.dto.response;

import com.smartlab.enums.ProjectType;
import com.smartlab.enums.PublicProjectStatus;

import java.time.LocalDate;
import java.util.List;

public record PublicProjectDetailResponse(
        Long id,
        String code,
        String name,
        String description,
        String goal,
        ProjectType projectType,
        PublicProjectStatus publicStatus,
        LocalDate startDate,
        LocalDate expectedEndDate,
        LocalDate actualEndDate,
        Boolean isFeatured,
        List<ProjectResearchFieldResponse> researchFields,
        PublicProjectLeaderResponse primaryLeader,
        List<PublicProjectLeaderResponse> leaders
) { }
