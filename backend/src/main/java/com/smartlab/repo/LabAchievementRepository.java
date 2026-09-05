package com.smartlab.repo;

import com.smartlab.entity.LabAchievementEntity;
import com.smartlab.enums.AchievementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LabAchievementRepository extends JpaRepository<LabAchievementEntity, Long> {
    @Query("""
            select a from LabAchievementEntity a where a.deletedAt is null and a.isPublic = true
            and a.achievementYear = :year order by a.achievementDate desc nulls last, a.createdAt desc, a.id desc
            """)
    Page<LabAchievementEntity> findPublicByYear(@Param("year") int year, Pageable pageable);

    @Query("""
            select a.achievementYear, count(a) from LabAchievementEntity a
            where a.deletedAt is null and a.isPublic = true group by a.achievementYear order by a.achievementYear desc
            """)
    List<Object[]> findPublicYearCounts();

    @Query("select max(a.achievementYear) from LabAchievementEntity a where a.deletedAt is null and a.isPublic = true")
    Integer findNewestPublicYear();

    @Query("""
            select a from LabAchievementEntity a
            where a.deletedAt is null
            and (:year is null or a.achievementYear = :year)
            and (:type is null or a.achievementType = :type)
            and (:isPublic is null or a.isPublic = :isPublic)
            and (:query = '' or lower(a.title) like concat('%', lower(:query), '%')
                 or lower(a.summary) like concat('%', lower(:query), '%'))
            order by a.achievementYear desc, a.achievementDate desc nulls last, a.updatedAt desc, a.id desc
            """)
    Page<LabAchievementEntity> findActiveForAdmin(@Param("year") Integer year, @Param("type") AchievementType type,
            @Param("isPublic") Boolean isPublic, @Param("query") String query, Pageable pageable);

    Optional<LabAchievementEntity> findByIdAndDeletedAtIsNull(Long id);
}
