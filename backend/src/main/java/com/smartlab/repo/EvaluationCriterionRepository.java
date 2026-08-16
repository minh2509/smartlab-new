package com.smartlab.repo;

import com.smartlab.entity.EvaluationCriterionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationCriterionRepository extends JpaRepository<EvaluationCriterionEntity, Long> {

    List<EvaluationCriterionEntity> findAllByProject_IdOrderByDisplayOrderAsc(Long projectId);

    Optional<EvaluationCriterionEntity> findByIdAndProject_Id(Long id, Long projectId);
}
