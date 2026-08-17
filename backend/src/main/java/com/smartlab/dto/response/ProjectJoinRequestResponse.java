package com.smartlab.dto.response;

import com.smartlab.enums.ProjectJoinRequestStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ProjectJoinRequestResponse {
    private Long id;
    private Long projectId;
    private String projectCode;
    private String projectName;
    private String requesterUserId;
    private String requesterName;
    private String requesterEmail;
    private String message;
    private ProjectJoinRequestStatus status;
    private String reviewedByUserId;
    private String reviewedByName;
    private Instant reviewedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
