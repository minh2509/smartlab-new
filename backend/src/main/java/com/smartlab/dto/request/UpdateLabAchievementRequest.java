package com.smartlab.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.smartlab.enums.AchievementType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateLabAchievementRequest {
    @Size(min = 1, max = 500) private String title;
    @Size(max = 20_000) private String summary;
    private AchievementType achievementType;
    @Min(value = 2023, message = "Achievement year must be between 2023 and the current year") private Integer achievementYear;
    private LocalDate achievementDate;
    @Size(max = 2048) @Pattern(regexp = "^https?://.+$", message = "Evidence URL must be an HTTP(S) URL") private String evidenceUrl;
    @Size(max = 500) private String recognizingOrganization;
    private Long relatedProjectId;
    private Boolean isPublic;
    @JsonIgnore private boolean summaryPresent;
    @JsonIgnore private boolean achievementDatePresent;
    @JsonIgnore private boolean evidenceUrlPresent;
    @JsonIgnore private boolean relatedProjectIdPresent;
    @JsonIgnore private boolean recognizingOrganizationPresent;

    @JsonSetter("summary") public void setSummary(String value) { summary = value; summaryPresent = true; }
    @JsonSetter("achievementDate") public void setAchievementDate(LocalDate value) { achievementDate = value; achievementDatePresent = true; }
    @JsonSetter("evidenceUrl") public void setEvidenceUrl(String value) { evidenceUrl = value; evidenceUrlPresent = true; }
    @JsonSetter("relatedProjectId") public void setRelatedProjectId(Long value) { relatedProjectId = value; relatedProjectIdPresent = true; }
    @JsonSetter("recognizingOrganization") public void setRecognizingOrganization(String value) { recognizingOrganization = value; recognizingOrganizationPresent = true; }
}
