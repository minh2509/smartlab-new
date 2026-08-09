package com.smartlab.repo;

import com.smartlab.entity.PostReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA infrastructure exposes inherited delete-capable methods, but Post Review application code
 * must treat review history as append-only and never expose review update or delete behavior.
 */
@Repository
public interface PostReviewRepository extends JpaRepository<PostReviewEntity, Long> {
}
