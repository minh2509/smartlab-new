package com.smartlab.dto.response;

import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ProjectMemberResponse {
    private String userId;
    private String name;
    private String email;
    private ProjectRole projectRole;
    private ProjectMemberStatus status;
    private Instant joinedAt;
    private Instant removedAt;
}
