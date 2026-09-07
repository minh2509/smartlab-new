package com.smartlab.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "Request body for creating a project, optionally with primary and additional leaders.")
public class CreateProjectRequest {
    @NotBlank(message = "Project code is required")
    @Size(max = 60, message = "Project code must not exceed 60 characters")
    @Schema(description = "Unique project code", example = "SL-AI-2026")
    private String code;

    @NotBlank(message = "Project name is required")
    @Size(max = 200, message = "Project name must not exceed 200 characters")
    @Schema(description = "Project name", example = "Smart attendance using computer vision")
    private String name;

    @Schema(description = "Project description")
    private String description;

    @Schema(description = "Project goal")
    private String goal;

    @Schema(description = "Project type. Defaults to RESEARCH when omitted.", example = "RESEARCH")
    private ProjectType projectType;

    @Schema(description = "Initial project status. Defaults to PROPOSED when omitted.", example = "PROPOSED")
    private ProjectStatus status;

    @Schema(description = "Project start date", example = "2026-08-15")
    private LocalDate startDate;

    @Schema(description = "Expected completion date", example = "2027-01-31")
    private LocalDate expectedEndDate;

    @Schema(description = "Actual completion date", example = "2027-01-20")
    private LocalDate actualEndDate;

    @Schema(description = "Whether the project is visible publicly. Defaults to false.", example = "false")
    private Boolean isPublic;

    @Schema(description = "Whether the project is featured. Defaults to false.", example = "false")
    private Boolean isFeatured;

    @Schema(description = "Whether this project is currently recruiting members. Defaults to false.", example = "false")
    private Boolean isRecruiting;

    @Schema(
            description = "Optional public user id of the primary leader. Omit it to assign a primary leader later.",
            example = "d3151812-a9d9-4244-bd51-6c62d2f11c92"
    )
    @Size(max = 36, message = "Primary leader user id must not exceed 36 characters")
    private String leaderUserId;

    @Size(max = 100, message = "At most 100 additional project leaders may be selected")
    @Schema(description = "Optional public user ids of additional leaders")
    private List<
            @NotBlank(message = "Additional leader user id must not be blank")
            @Size(max = 36, message = "Additional leader user id must not exceed 36 characters") String>
            additionalLeaderUserIds;

    @JsonIgnore
    @AssertTrue(message = "At most 100 project leaders may be selected")
    public boolean isLeaderLimitValid() {
        java.util.Set<String> leaderIds = new java.util.HashSet<>();
        if (leaderUserId != null && !leaderUserId.isBlank()) {
            leaderIds.add(leaderUserId.trim());
        }
        if (additionalLeaderUserIds != null) {
            additionalLeaderUserIds.stream()
                    .filter(userId -> userId != null && !userId.isBlank())
                    .map(String::trim)
                    .forEach(leaderIds::add);
        }
        return leaderIds.size() <= 100;
    }
}
