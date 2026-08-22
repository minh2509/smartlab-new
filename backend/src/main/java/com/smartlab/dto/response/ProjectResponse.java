package com.smartlab.dto.response;

import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@Schema(description = "Project details with its primary leader and complete leader set.")
public class ProjectResponse {
    @Schema(description = "Internal project id", example = "1")
    private Long id;

    @Schema(description = "Unique project code", example = "SL-AI-2026")
    private String code;

    @Schema(description = "Project name", example = "Smart attendance using computer vision")
    private String name;

    private String description;
    private String goal;
    private ProjectType projectType;
    private ProjectStatus status;
    private LocalDate startDate;
    private LocalDate expectedEndDate;
    private LocalDate actualEndDate;
    private Boolean isPublic;
    private Boolean isFeatured;
    private Boolean isRecruiting;

    @Schema(description = "Primary leader retained for project ownership metadata")
    private ProjectLeaderResponse primaryLeader;

    @Schema(description = "All equal-permission project leaders, including the primary leader")
    private List<ProjectLeaderResponse> leaders;

    private Instant createdAt;
    private Instant updatedAt;
}
