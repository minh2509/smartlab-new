package com.smartlab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EvaluationScoreId implements Serializable {

    @Column(name = "evaluation_id")
    private Long evaluationId;

    @Column(name = "criterion_id")
    private Long criterionId;
}
