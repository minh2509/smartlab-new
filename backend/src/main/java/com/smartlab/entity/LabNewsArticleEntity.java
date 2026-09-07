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
@Table(name = "lab_news_articles")
public class LabNewsArticleEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500) private String title;
    @Column(columnDefinition = "TEXT") private String excerpt;
    @Column(name = "source_name", nullable = false, length = 255) private String sourceName;
    @Column(name = "source_url", nullable = false, length = 2048) private String sourceUrl;
    @Column(name = "published_at", nullable = false) private Instant publishedAt;
    @Column(name = "is_public", nullable = false) private Boolean isPublic;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    public static LabNewsArticleEntity create(String title, String excerpt, String sourceName, String sourceUrl,
            Instant publishedAt, Boolean isPublic, Instant now) {
        LabNewsArticleEntity entity = new LabNewsArticleEntity();
        entity.createdAt = now;
        entity.apply(title, excerpt, sourceName, sourceUrl, publishedAt, isPublic, now);
        return entity;
    }

    public void update(String title, String excerpt, String sourceName, String sourceUrl, Instant publishedAt,
            Boolean isPublic, Instant now) {
        apply(title, excerpt, sourceName, sourceUrl, publishedAt, isPublic, now);
    }

    public void softDelete(Instant now) {
        deletedAt = now;
        updatedAt = now;
    }

    private void apply(String title, String excerpt, String sourceName, String sourceUrl, Instant publishedAt,
            Boolean isPublic, Instant now) {
        this.title = title;
        this.excerpt = excerpt;
        this.sourceName = sourceName;
        this.sourceUrl = sourceUrl;
        this.publishedAt = publishedAt;
        this.isPublic = isPublic;
        this.updatedAt = now;
    }
}
