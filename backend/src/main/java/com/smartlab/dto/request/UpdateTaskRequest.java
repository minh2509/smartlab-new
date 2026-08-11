package com.smartlab.dto.request;

import com.smartlab.enums.TaskPriority;
import com.smartlab.enums.TaskStatus;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class UpdateTaskRequest {

    @Size(max = 255)
    private String title;

    private String description;

    private TaskStatus status;

    private TaskPriority priority;

    private Long parentTaskId;

    private Instant startAt;

    private Instant dueAt;
}
