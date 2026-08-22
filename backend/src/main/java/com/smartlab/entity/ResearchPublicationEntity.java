package com.smartlab.entity;

import com.smartlab.enums.PublicationType;
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
import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "research_publications")
public class ResearchPublicationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String authors;
    @Enumerated(EnumType.STRING) @Column(name = "publication_type", nullable = false, length = 30)
    private PublicationType publicationType;
    @Column(nullable = false, length = 500) private String venue;
    @Column(name = "publication_year", nullable = false) private Integer publicationYear;
    @Column(name = "publication_date") private LocalDate publicationDate;
    @Column(length = 255) private String doi;
    @Column(name = "public_url", length = 2048) private String publicUrl;
    @Column(columnDefinition = "TEXT") private String summary;
    @Column(name = "is_public", nullable = false) private Boolean isPublic;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    public static ResearchPublicationEntity create(String title, String authors, PublicationType publicationType,
            String venue, Integer publicationYear, LocalDate publicationDate, String doi, String publicUrl,
            String summary, Boolean isPublic, Instant now) {
        ResearchPublicationEntity entity = new ResearchPublicationEntity();
        entity.apply(title, authors, publicationType, venue, publicationYear, publicationDate, doi, publicUrl, summary, isPublic, now);
        entity.createdAt = now;
        return entity;
    }

    public void update(String title, String authors, PublicationType publicationType, String venue,
            Integer publicationYear, LocalDate publicationDate, String doi, String publicUrl, String summary,
            Boolean isPublic, Instant now) {
        apply(title, authors, publicationType, venue, publicationYear, publicationDate, doi, publicUrl, summary, isPublic, now);
    }

    public void softDelete(Instant now) { this.deletedAt = now; this.updatedAt = now; }

    private void apply(String title, String authors, PublicationType publicationType, String venue,
            Integer publicationYear, LocalDate publicationDate, String doi, String publicUrl, String summary,
            Boolean isPublic, Instant now) {
        this.title = title; this.authors = authors; this.publicationType = publicationType; this.venue = venue;
        this.publicationYear = publicationYear; this.publicationDate = publicationDate; this.doi = doi;
        this.publicUrl = publicUrl; this.summary = summary; this.isPublic = isPublic; this.updatedAt = now;
    }
}
