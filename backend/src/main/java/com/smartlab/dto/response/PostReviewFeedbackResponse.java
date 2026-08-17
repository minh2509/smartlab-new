package com.smartlab.dto.response;

import com.smartlab.enums.ReviewDecision;
import lombok.Value;

import java.time.Instant;

@Value
public class PostReviewFeedbackResponse {
    ReviewDecision decision;
    String reason;
    Instant createdAt;
}
