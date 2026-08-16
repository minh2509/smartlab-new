package com.smartlab.repo;

import com.smartlab.entity.EvaluationScoreEntity;
import com.smartlab.entity.EvaluationScoreId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EvaluationScoreRepository extends JpaRepository<EvaluationScoreEntity, EvaluationScoreId> {
}
