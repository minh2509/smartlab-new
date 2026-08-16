package com.smartlab.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class EvaluationCriterionResponse {
    private Long id;
    private Long projectId;
    private String name;
    private String description;
    private BigDecimal maxScore;
    private Integer displayOrder;
    private Boolean isActive;
    private Long createdByUserId;
    private String createdByName;
    private Instant createdAt;
}
