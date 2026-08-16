package com.smartlab.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateEvaluationRequest {

    @NotBlank(message = "Evaluated user id is required")
    private String evaluatedUserId;

    private String note;

    @NotEmpty
    @Valid
    private List<ScoreEntry> scores;

    @Data
    public static class ScoreEntry {
        @NotNull
        private Long criterionId;

        @NotNull
        @DecimalMin("0")
        private BigDecimal score;

        private String note;
    }
}
