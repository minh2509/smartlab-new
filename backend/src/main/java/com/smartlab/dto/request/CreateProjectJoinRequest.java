package com.smartlab.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateProjectJoinRequest {
    @Size(max = 500, message = "Join request message must not exceed 500 characters")
    private String message;
}
