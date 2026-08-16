package com.smartlab.dto.request;

import com.smartlab.enums.TaskPriority;
import com.smartlab.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class CreateTaskRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    private String description;

    @NotNull
    private TaskPriority priority;

    private TaskStatus status;

    private Long parentTaskId;

    private Instant startAt;

    private Instant dueAt;
}
