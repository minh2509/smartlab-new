package com.smartlab.repo;

import com.smartlab.entity.PostReviewEntity;
import com.smartlab.enums.ReviewDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA infrastructure exposes inherited delete-capable methods, but Post Review application code
 * must treat review history as append-only and never expose review update or delete behavior.
 */
@Repository
public interface PostReviewRepository extends JpaRepository<PostReviewEntity, Long> {
    Optional<PostReviewEntity> findFirstByPostIdAndDecisionOrderByCreatedAtDescIdDesc(Long postId, ReviewDecision decision);
}
