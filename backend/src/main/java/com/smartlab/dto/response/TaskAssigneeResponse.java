package com.smartlab.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class TaskAssigneeResponse {
    private Long userId;
    private String name;
    private String email;
    private Instant assignedAt;
}
