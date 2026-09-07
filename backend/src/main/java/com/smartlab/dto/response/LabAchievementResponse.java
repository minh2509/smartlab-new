package com.smartlab.dto.response;

import com.smartlab.enums.AchievementType;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;

@Builder
public record LabAchievementResponse(Long id, String title, String summary, AchievementType achievementType,
        Integer achievementYear, LocalDate achievementDate, String evidenceUrl, String recognizingOrganization, PublicRelatedProjectResponse relatedProject,
        Boolean isPublic, Instant createdAt, Instant updatedAt) { }
