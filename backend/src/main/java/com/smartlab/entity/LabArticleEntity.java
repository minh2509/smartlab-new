package com.smartlab.entity;

import com.smartlab.enums.LabArticleStatus;
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
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "lab_articles")
public class LabArticleEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 500) private String title;
    @Column(nullable = false, length = 500) private String slug;
    @Column(columnDefinition = "TEXT") private String excerpt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb") private Map<String, Object> content;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private LabArticleStatus status;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    public static LabArticleEntity create(String title, String slug, String excerpt, Map<String, Object> content,
            LabArticleStatus status, Instant publishedAt, Instant now) {
        LabArticleEntity article = new LabArticleEntity();
        article.title = title;
        article.slug = slug;
        article.excerpt = excerpt;
        article.content = copyContent(content);
        article.status = status;
        article.publishedAt = publishedAt;
        article.createdAt = now;
        article.updatedAt = now;
        return article;
    }

    public void update(String title, String slug, String excerpt, Map<String, Object> content, LabArticleStatus status,
            Instant publishedAt, Instant now) {
        this.title = title;
        this.slug = slug;
        this.excerpt = excerpt;
        this.content = copyContent(content);
        this.status = status;
        this.publishedAt = publishedAt;
        this.updatedAt = now;
    }

    public void softDelete(Instant now) {
        deletedAt = now;
        updatedAt = now;
    }

    private static Map<String, Object> copyContent(Map<String, Object> content) {
        return new LinkedHashMap<>(content);
    }
}
