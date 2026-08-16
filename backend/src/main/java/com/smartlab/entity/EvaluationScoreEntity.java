package com.smartlab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "evaluation_scores")
public class EvaluationScoreEntity {

    @EmbeddedId
    private EvaluationScoreId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("evaluationId")
    @JoinColumn(name = "evaluation_id")
    private EvaluationEntity evaluation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("criterionId")
    @JoinColumn(name = "criterion_id")
    private EvaluationCriterionEntity criterion;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(columnDefinition = "TEXT")
    private String note;

    public static EvaluationScoreEntity of(
            EvaluationEntity evaluation,
            EvaluationCriterionEntity criterion,
            BigDecimal score,
            String note
    ) {
        return EvaluationScoreEntity.builder()
                .id(new EvaluationScoreId(evaluation.getId(), criterion.getId()))
                .evaluation(evaluation)
                .criterion(criterion)
                .score(score)
                .note(note)
                .build();
    }
}
