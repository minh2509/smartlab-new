package com.smartlab.dto.response;

import com.smartlab.enums.AchievementType;

import java.time.Instant;
import java.time.LocalDate;

public record AdminLabAchievementResponse(Long id, String title, String summary, AchievementType achievementType,
        Integer achievementYear, LocalDate achievementDate, String evidenceUrl, String recognizingOrganization, Long relatedProjectId,
        Boolean isPublic, Instant createdAt, Instant updatedAt) {
    public AdminLabAchievementResponse(Long id, String title, String summary, AchievementType achievementType,
            Integer achievementYear, LocalDate achievementDate, String evidenceUrl, Long relatedProjectId,
            Boolean isPublic, Instant createdAt, Instant updatedAt) {
        this(id, title, summary, achievementType, achievementYear, achievementDate, evidenceUrl, null, relatedProjectId,
                isPublic, createdAt, updatedAt);
    }
}
