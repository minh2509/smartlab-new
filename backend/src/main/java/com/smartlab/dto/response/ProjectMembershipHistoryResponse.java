package com.smartlab.dto.response;

import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import com.smartlab.enums.ProjectStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ProjectMembershipHistoryResponse {
    private Long projectId;
    private String projectCode;
    private String projectName;
    private ProjectStatus projectStatus;
    private ProjectRole projectRole;
    private ProjectMemberStatus status;
    private Instant joinedAt;
    private Instant removedAt;
}
