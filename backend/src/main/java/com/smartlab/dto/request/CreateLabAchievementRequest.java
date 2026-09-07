package com.smartlab.dto.request;

import com.smartlab.enums.AchievementType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateLabAchievementRequest {
    @NotBlank @Size(max = 500) private String title;
    @Size(max = 20_000) private String summary;
    @NotNull private AchievementType achievementType;
    @NotNull @Min(value = 2023, message = "Achievement year must be between 2023 and the current year") private Integer achievementYear;
    private LocalDate achievementDate;
    @Size(max = 2048) @Pattern(regexp = "^https?://.+$", message = "Evidence URL must be an HTTP(S) URL") private String evidenceUrl;
    @Size(max = 500) private String recognizingOrganization;
    private Long relatedProjectId;
    private Boolean isPublic;

    @AssertTrue(message = "Achievement date year must equal achievement year")
    public boolean isAchievementDateYearValid() {
        return achievementDate == null || achievementYear == null || achievementDate.getYear() == achievementYear;
    }
}
