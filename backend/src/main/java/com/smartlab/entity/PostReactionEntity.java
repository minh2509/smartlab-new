package com.smartlab.entity;

import com.smartlab.enums.PostReactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post_reactions", uniqueConstraints = @UniqueConstraint(
        name = "uk_post_reactions_post_user", columnNames = {"post_id", "user_id"}
))
public class PostReactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false, length = 20)
    private PostReactionType reactionType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PostReactionEntity create(Long postId, Long userId, PostReactionType reactionType, Instant now) {
        PostReactionEntity reaction = new PostReactionEntity();
        reaction.postId = postId;
        reaction.userId = userId;
        reaction.reactionType = reactionType;
        reaction.createdAt = now;
        reaction.updatedAt = now;
        return reaction;
    }

    public void changeTo(PostReactionType reactionType, Instant now) {
        if (this.reactionType == reactionType) {
            return;
        }
        this.reactionType = reactionType;
        this.updatedAt = now;
    }
}
