package com.smartlab.dto.response;

import com.smartlab.enums.AchievementType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PublicLabAchievementDetailResponse(
        Long id,
        String title,
        String summary,
        AchievementType achievementType,
        Integer achievementYear,
        LocalDate achievementDate,
        String evidenceUrl,
        String recognizingOrganization,
        PublicRelatedProjectResponse relatedProject,
        List<LabAchievementFileResponse> images,
        Instant createdAt,
        Instant updatedAt
) {
}
