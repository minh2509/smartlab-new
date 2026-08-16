package com.smartlab.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class UpdateEvaluationRequest {

    private String note;

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
