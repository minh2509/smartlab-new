package com.smartlab.dto.response;

import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class TaskDetailResponse {
    private Long id;
    private Long projectId;
    private Long parentTaskId;
    private String title;
    private String description;
    private String status;
    private String priority;
    private Instant startAt;
    private Instant dueAt;
    private Long createdByUserId;
    private String createdByName;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private List<TaskAssigneeResponse> assignees;
    private List<TaskAttachmentResponse> attachments;
}
