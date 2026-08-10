package com.smartlab.dto.request;

import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Partial project update. Omitted or null fields remain unchanged.")
public class UpdateProjectRequest {
    @Pattern(regexp = ".*\\S.*", message = "Project code must not be blank")
    @Size(max = 60, message = "Project code must not exceed 60 characters")
    @Schema(description = "Unique project code", example = "SL-AI-2026")
    private String code;

    @Pattern(regexp = ".*\\S.*", message = "Project name must not be blank")
    @Size(max = 200, message = "Project name must not exceed 200 characters")
    @Schema(description = "Project name", example = "Smart attendance using computer vision")
    private String name;

    @Schema(description = "Project description")
    private String description;

    @Schema(description = "Project goal")
    private String goal;

    @Schema(description = "Project type", example = "RESEARCH")
    private ProjectType projectType;

    @Schema(description = "Project status", example = "IN_PROGRESS")
    private ProjectStatus status;

    @Schema(description = "Project start date", example = "2026-08-15")
    private LocalDate startDate;

    @Schema(description = "Expected completion date", example = "2027-01-31")
    private LocalDate expectedEndDate;

    @Schema(description = "Actual completion date", example = "2027-01-20")
    private LocalDate actualEndDate;

    @Schema(description = "Whether the project is visible publicly", example = "true")
    private Boolean isPublic;

    @Schema(description = "Whether the project is featured", example = "true")
    private Boolean isFeatured;
}
