package com.smartlab.entity;

import com.smartlab.enums.AchievementType;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "lab_achievements")
public class LabAchievementEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500) private String title;
    @Column(columnDefinition = "TEXT") private String summary;
    @Enumerated(EnumType.STRING) @Column(name = "achievement_type", nullable = false, length = 30)
    private AchievementType achievementType;
    @Column(name = "achievement_year", nullable = false) private Integer achievementYear;
    @Column(name = "achievement_date") private LocalDate achievementDate;
    @Column(name = "evidence_url", length = 2048) private String evidenceUrl;
    @Column(name = "recognizing_organization", length = 500) private String recognizingOrganization;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "related_project_id")
    private ProjectEntity relatedProject;
    @Column(name = "is_public", nullable = false) private Boolean isPublic;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    public static LabAchievementEntity create(String title, String summary, AchievementType achievementType,
            Integer achievementYear, LocalDate achievementDate, String evidenceUrl, String recognizingOrganization, ProjectEntity relatedProject,
            Boolean isPublic, Instant now) {
        LabAchievementEntity entity = new LabAchievementEntity();
        entity.apply(title, summary, achievementType, achievementYear, achievementDate, evidenceUrl, recognizingOrganization, relatedProject, isPublic, now);
        entity.createdAt = now;
        return entity;
    }

    public static LabAchievementEntity create(String title, String summary, AchievementType achievementType,
            Integer achievementYear, LocalDate achievementDate, String evidenceUrl, ProjectEntity relatedProject,
            Boolean isPublic, Instant now) {
        return create(title, summary, achievementType, achievementYear, achievementDate, evidenceUrl, null, relatedProject, isPublic, now);
    }

    public void update(String title, String summary, AchievementType achievementType, Integer achievementYear,
            LocalDate achievementDate, String evidenceUrl, String recognizingOrganization, ProjectEntity relatedProject, Boolean isPublic, Instant now) {
        apply(title, summary, achievementType, achievementYear, achievementDate, evidenceUrl, recognizingOrganization, relatedProject, isPublic, now);
    }

    public void softDelete(Instant now) { deletedAt = now; updatedAt = now; }

    public Long getRelatedProjectId() { return relatedProject == null ? null : relatedProject.getId(); }

    private void apply(String title, String summary, AchievementType achievementType, Integer achievementYear,
            LocalDate achievementDate, String evidenceUrl, String recognizingOrganization, ProjectEntity relatedProject, Boolean isPublic, Instant now) {
        this.title = title; this.summary = summary; this.achievementType = achievementType;
        this.achievementYear = achievementYear; this.achievementDate = achievementDate; this.evidenceUrl = evidenceUrl; this.recognizingOrganization = recognizingOrganization;
        this.relatedProject = relatedProject; this.isPublic = isPublic; this.updatedAt = now;
    }
}
