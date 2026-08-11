package com.smartlab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post_comments")
public class PostCommentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(name = "content", nullable = false, length = 5000)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static PostCommentEntity create(Long postId, Long authorUserId, String content, Instant now) {
        PostCommentEntity comment = new PostCommentEntity();
        comment.postId = postId;
        comment.authorUserId = authorUserId;
        comment.content = content;
        comment.createdAt = now;
        comment.updatedAt = now;
        return comment;
    }

    public void edit(String content, Instant now) {
        this.content = content;
        this.updatedAt = now;
    }

    public void softDelete(Instant now) {
        if (deletedAt == null) {
            deletedAt = now;
            updatedAt = now;
        }
    }
}
