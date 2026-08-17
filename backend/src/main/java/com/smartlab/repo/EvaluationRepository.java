package com.smartlab.repo;

import com.smartlab.entity.EvaluationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationRepository extends JpaRepository<EvaluationEntity, Long> {

    @Query("""
            select e from EvaluationEntity e
            join fetch e.evaluator
            join fetch e.project
            left join fetch e.scores s
            left join fetch s.criterion
            where e.evaluatedUser.id = :userId
            order by e.createdAt desc
            """)
    List<EvaluationEntity> findAllByEvaluatedUserId(@Param("userId") Long userId);

    @Query("""
            select e from EvaluationEntity e
            join fetch e.evaluator
            join fetch e.evaluatedUser
            join fetch e.project
            left join fetch e.scores s
            left join fetch s.criterion
            where e.id = :id
            """)
    Optional<EvaluationEntity> findByIdWithScores(@Param("id") Long id);
}
