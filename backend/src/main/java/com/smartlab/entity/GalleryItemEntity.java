package com.smartlab.entity;

import com.smartlab.enums.GalleryCategory;
import com.smartlab.enums.GalleryItemStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "gallery_items")
public class GalleryItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private StoredFileEntity file;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String caption;

    @Column(name = "alt_text", nullable = false, length = 255)
    private String altText;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private GalleryCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private ProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private EventEntity event;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GalleryItemStatus status;

    @Column(name = "is_featured", nullable = false)
    private Boolean isFeatured;

    @Column(name = "published_at")
    private Instant publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private UserEntity createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static GalleryItemEntity draft(
            StoredFileEntity file,
            String title,
            String caption,
            String altText,
            GalleryCategory category,
            ProjectEntity project,
            EventEntity event,
            Instant capturedAt,
            Boolean isFeatured,
            UserEntity createdBy
    ) {
        return GalleryItemEntity.builder()
                .file(file)
                .title(title)
                .caption(caption)
                .altText(altText)
                .category(category == null ? GalleryCategory.OTHER : category)
                .project(project)
                .event(event)
                .capturedAt(capturedAt)
                .status(GalleryItemStatus.DRAFT)
                .isFeatured(Boolean.TRUE.equals(isFeatured))
                .createdBy(createdBy)
                .build();
    }

    public void updateMetadata(String title, String caption, String altText, GalleryCategory category,
                               ProjectEntity project, EventEntity event, Instant capturedAt, Boolean featured) {
        this.title = title;
        this.caption = caption;
        this.altText = altText;
        this.category = category == null ? GalleryCategory.OTHER : category;
        this.project = project;
        this.event = event;
        this.capturedAt = capturedAt;
        this.isFeatured = Boolean.TRUE.equals(featured);
    }

    public void publish(Instant now) {
        this.status = GalleryItemStatus.PUBLISHED;
        this.publishedAt = now;
    }

    public void unpublish() {
        this.status = GalleryItemStatus.DRAFT;
        this.publishedAt = null;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.status = GalleryItemStatus.DRAFT;
        this.publishedAt = null;
    }

    public boolean isActive() {
        return deletedAt == null;
    }
}
