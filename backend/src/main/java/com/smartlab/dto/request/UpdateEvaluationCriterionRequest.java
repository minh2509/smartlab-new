package com.smartlab.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateEvaluationCriterionRequest {

    @Size(max = 150)
    private String name;

    private String description;

    @DecimalMin("0.01")
    private BigDecimal maxScore;

    private Integer displayOrder;

    private Boolean isActive;
}
