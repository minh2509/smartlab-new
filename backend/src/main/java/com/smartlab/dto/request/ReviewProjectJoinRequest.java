package com.smartlab.dto.request;

import com.smartlab.enums.ProjectJoinRequestDecision;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewProjectJoinRequest {
    @NotNull(message = "Join request decision is required")
    private ProjectJoinRequestDecision decision;
}
