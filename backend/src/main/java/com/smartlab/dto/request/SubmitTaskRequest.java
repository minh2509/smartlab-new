package com.smartlab.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmitTaskRequest {

    @NotNull
    private Long fileId;

    private String note;
}
