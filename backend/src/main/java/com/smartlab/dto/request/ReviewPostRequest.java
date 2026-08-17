package com.smartlab.dto.request;

import com.smartlab.enums.ReviewDecision;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewPostRequest(
        @NotNull(message = "Review decision is required") ReviewDecision decision,
        @Size(max = 1000, message = "Reason must be at most 1000 characters") String reason
) {
    @AssertTrue(message = "Reason must be non-blank when decision is REVISION_REQUIRED or REJECTED")
    public boolean isRequiredReasonValid() {
        return (decision != ReviewDecision.REVISION_REQUIRED && decision != ReviewDecision.REJECTED)
                || (reason != null && !reason.trim().isEmpty());
    }
}
