package com.smartlab.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

@Data
@Builder
public class EvaluationResponse {
    private Long id;
    private Long projectId;
    private String projectName;
    private Long evaluatorUserId;
    private String evaluatorName;
    private Long evaluatedUserId;
    private String evaluatedUserName;
    private String note;
    private List<ScoreItem> scores;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    @Data
    @Builder
    public static class ScoreItem {
        private Long criterionId;
        private String criterionName;
        private BigDecimal maxScore;
        private BigDecimal score;
        private String note;
    }
}
