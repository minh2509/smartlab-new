package com.smartlab.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "posts")
public class PostEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_user_id")
    private Long authorUserId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "title", nullable = false, length = 250)
    private String title;

    @Column(name = "slug", nullable = false, unique = true, length = 260)
    private String slug;

    @Column(name = "excerpt", length = 500)
    private String excerpt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode contentJson;

    @Column(name = "content_html", columnDefinition = "TEXT")
    private String contentHtml;

    @Column(name = "cover_file_id")
    private Long coverFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private PostVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PostStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static PostEntity createDraft(
            Long authorUserId,
            String title,
            String slug,
            String excerpt,
            JsonNode contentJson,
            PostVisibility visibility,
            Long categoryId,
            Instant creationTime
    ) {
        PostEntity post = new PostEntity();
        post.authorUserId = authorUserId;
        post.title = title;
        post.slug = slug;
        post.excerpt = excerpt;
        post.contentJson = contentJson;
        post.visibility = visibility;
        post.categoryId = categoryId;
        post.status = PostStatus.DRAFT;
        post.createdAt = creationTime;
        post.updatedAt = creationTime;
        return post;
    }
}
