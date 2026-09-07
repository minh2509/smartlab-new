package com.smartlab.repo;

import com.smartlab.entity.LabAchievementFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LabAchievementFileRepository extends JpaRepository<LabAchievementFileEntity, Long> {
    @Query("select af from LabAchievementFileEntity af join fetch af.file where af.achievement.id = :achievementId and af.deletedAt is null and af.file.deletedAt is null order by af.sortOrder asc, af.id asc")
    List<LabAchievementFileEntity> findActiveByAchievementId(@Param("achievementId") Long achievementId);

    @Query("select af from LabAchievementFileEntity af join fetch af.file where af.id = :attachmentId and af.achievement.id = :achievementId and af.deletedAt is null")
    Optional<LabAchievementFileEntity> findActiveByIdAndAchievementId(@Param("attachmentId") Long attachmentId, @Param("achievementId") Long achievementId);

    @Query("select af from LabAchievementFileEntity af join fetch af.file where af.id = :attachmentId and af.achievement.id = :achievementId and af.deletedAt is null and af.file.deletedAt is null and af.file.accessScope = 'PUBLIC'")
    Optional<LabAchievementFileEntity> findPublicActiveByIdAndAchievementId(@Param("attachmentId") Long attachmentId, @Param("achievementId") Long achievementId);

    @Query("select coalesce(max(af.sortOrder), -1) from LabAchievementFileEntity af where af.achievement.id = :achievementId and af.deletedAt is null")
    Integer findMaxActiveSortOrder(@Param("achievementId") Long achievementId);

    @Query("select case when count(af) > 0 then true else false end from LabAchievementFileEntity af where af.file.id = :fileId and af.deletedAt is null and af.achievement.deletedAt is null")
    boolean existsActiveReferenceForActiveAchievement(@Param("fileId") Long fileId);
}
