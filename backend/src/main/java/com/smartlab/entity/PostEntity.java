package com.smartlab.entity;

import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private Map<String, Object> contentJson;

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
            Map<String, Object> contentJson,
            PostVisibility visibility,
            Long categoryId,
            Instant creationTime
    ) {
        return createDraft(
                authorUserId,
                title,
                slug,
                excerpt,
                contentJson,
                null,
                visibility,
                categoryId,
                null,
                creationTime
        );
    }

    public static PostEntity createDraft(
            Long authorUserId,
            String title,
            String slug,
            String excerpt,
            Map<String, Object> contentJson,
            String contentHtml,
            PostVisibility visibility,
            Long categoryId,
            Instant creationTime
    ) {
        return createDraft(
                authorUserId,
                title,
                slug,
                excerpt,
                contentJson,
                contentHtml,
                visibility,
                categoryId,
                null,
                creationTime
        );
    }

    public static PostEntity createDraft(
            Long authorUserId,
            String title,
            String slug,
            String excerpt,
            Map<String, Object> contentJson,
            String contentHtml,
            PostVisibility visibility,
            Long categoryId,
            Long projectId,
            Instant creationTime
    ) {
        PostEntity post = new PostEntity();
        post.authorUserId = authorUserId;
        post.title = title;
        post.slug = slug;
        post.excerpt = excerpt;
        post.contentJson = copyJsonObject(contentJson);
        post.contentHtml = contentHtml;
        post.visibility = visibility;
        post.categoryId = categoryId;
        post.projectId = projectId;
        post.status = PostStatus.DRAFT;
        post.createdAt = creationTime;
        post.updatedAt = creationTime;
        return post;
    }

    public void applyDraftUpdate(
            String title,
            String excerpt,
            Map<String, Object> contentJson,
            PostVisibility visibility,
            Long categoryId,
            Instant updatedAt
    ) {
        applyDraftUpdate(
                title,
                excerpt,
                contentJson,
                this.contentHtml,
                visibility,
                categoryId,
                this.projectId,
                updatedAt
        );
    }

    public void applyDraftUpdate(
            String title,
            String excerpt,
            Map<String, Object> contentJson,
            String contentHtml,
            PostVisibility visibility,
            Long categoryId,
            Instant updatedAt
    ) {
        applyDraftUpdate(
                title,
                excerpt,
                contentJson,
                contentHtml,
                visibility,
                categoryId,
                this.projectId,
                updatedAt
        );
    }

    public void applyDraftUpdate(
            String title,
            String excerpt,
            Map<String, Object> contentJson,
            String contentHtml,
            PostVisibility visibility,
            Long categoryId,
            Long projectId,
            Instant updatedAt
    ) {
        if (status != PostStatus.DRAFT && status != PostStatus.REVISION_REQUIRED) {
            throw new IllegalStateException("Only draft or revision-required posts can be updated");
        }

        this.title = title;
        this.excerpt = excerpt;
        this.contentJson = copyJsonObject(contentJson);
        this.contentHtml = contentHtml;
        this.visibility = visibility;
        this.categoryId = categoryId;
        this.projectId = projectId;
        this.updatedAt = updatedAt;
    }

    public void submitForReview(Instant mutationInstant) {
        Objects.requireNonNull(mutationInstant, "Mutation instant is required");
        if (status != PostStatus.DRAFT && status != PostStatus.REVISION_REQUIRED) {
            throw new IllegalStateException("Only draft or revision-required posts can be submitted for review");
        }

        this.status = PostStatus.PENDING_REVIEW;
        this.updatedAt = mutationInstant;
    }

    public void applyReviewDecision(ReviewDecision decision, Instant mutationInstant) {
        Objects.requireNonNull(decision, "Review decision is required");
        Objects.requireNonNull(mutationInstant, "Mutation instant is required");
        if (status != PostStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Only pending-review posts can be reviewed");
        }

        this.status = switch (decision) {
            case APPROVED -> PostStatus.PUBLISHED;
            case REVISION_REQUIRED -> PostStatus.REVISION_REQUIRED;
            case REJECTED -> PostStatus.REJECTED;
        };
        if (decision == ReviewDecision.APPROVED) {
            this.publishedAt = mutationInstant;
        }
        this.updatedAt = mutationInstant;
    }

    public void publish(Instant mutationInstant) {
        Objects.requireNonNull(mutationInstant, "Mutation instant is required");
        if (status != PostStatus.APPROVED) {
            throw new IllegalStateException("Only approved posts can be published");
        }

        this.status = PostStatus.PUBLISHED;
        this.publishedAt = mutationInstant;
        this.updatedAt = mutationInstant;
    }

    public void publishDirect(Instant mutationInstant) {
        Objects.requireNonNull(mutationInstant, "Mutation instant is required");
        if (status != PostStatus.DRAFT) {
            throw new IllegalStateException("Only draft posts can be directly published");
        }

        this.status = PostStatus.PUBLISHED;
        this.publishedAt = mutationInstant;
        this.updatedAt = mutationInstant;
    }

    private static Map<String, Object> copyJsonObject(Map<String, Object> source) {
        if (source == null) {
            return null;
        }

        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, copyJsonValue(value)));
        return copy;
    }

    private static Object copyJsonValue(Object value) {
        if (value instanceof Map<?, ?> nestedMap) {
            Map<String, Object> copy = new LinkedHashMap<>();
            nestedMap.forEach((key, nestedValue) -> {
                if (!(key instanceof String stringKey)) {
                    throw new IllegalArgumentException("JSON object keys must be strings");
                }
                copy.put(stringKey, copyJsonValue(nestedValue));
            });
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(copyJsonValue(item)));
            return copy;
        }
        return value;
    }
}
