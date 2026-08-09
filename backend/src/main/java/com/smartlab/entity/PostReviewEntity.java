package com.smartlab.entity;

import com.smartlab.enums.ReviewDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post_reviews")
public class PostReviewEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "reviewer_user_id")
    private Long reviewerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private ReviewDecision decision;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PostReviewEntity create(
            Long postId,
            Long reviewerUserId,
            ReviewDecision decision,
            String reason,
            Instant createdAt
    ) {
        requirePositiveId(postId, "Post ID");
        requirePositiveId(reviewerUserId, "Reviewer user ID");
        Objects.requireNonNull(decision, "Review decision is required");
        Objects.requireNonNull(createdAt, "Review creation instant is required");
        validateReason(decision, reason);

        PostReviewEntity review = new PostReviewEntity();
        review.postId = postId;
        review.reviewerUserId = reviewerUserId;
        review.decision = decision;
        review.reason = reason;
        review.createdAt = createdAt;
        return review;
    }

    private static void requirePositiveId(Long value, String label) {
        Objects.requireNonNull(value, label + " is required");
        if (value <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }

    private static void validateReason(ReviewDecision decision, String reason) {
        if (reason != null && reason.length() > 1000) {
            throw new IllegalArgumentException("Review reason must be at most 1000 characters");
        }
        if (decision == ReviewDecision.REJECTED && (reason == null || reason.trim().isEmpty())) {
            throw new IllegalArgumentException("Rejected review reason must be non-blank");
        }
    }
}
