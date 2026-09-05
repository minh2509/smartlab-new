package com.smartlab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "lab_achievement_files")
public class LabAchievementFileEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "achievement_id", nullable = false) private LabAchievementEntity achievement;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "file_id", nullable = false) private StoredFileEntity file;
    @Column(length = 500) private String label;
    @Column(name = "sort_order", nullable = false) private Integer sortOrder;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    public static LabAchievementFileEntity create(LabAchievementEntity achievement, StoredFileEntity file, String label,
                                                   int sortOrder, Instant now) {
        LabAchievementFileEntity entity = new LabAchievementFileEntity();
        entity.achievement = achievement; entity.file = file; entity.label = label; entity.sortOrder = sortOrder; entity.createdAt = now;
        return entity;
    }

    public void softDetach(Instant now) { deletedAt = now; }
}
