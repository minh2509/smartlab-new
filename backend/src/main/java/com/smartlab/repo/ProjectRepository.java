package com.smartlab.repo;

import com.smartlab.entity.ProjectEntity;
import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {
    Optional<ProjectEntity> findByIdAndDeletedAtIsNull(Long id);

    List<ProjectEntity> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

    @Query("""
            select p from ProjectEntity p
            where p.deletedAt is null
              and p.isPublic = true
              and coalesce(p.isRecruiting, false) = true
              and p.status in :statuses
            order by p.createdAt desc, p.id desc
            """)
    Page<ProjectEntity> findPublicRecruitingProjects(
            @Param("statuses") List<ProjectStatus> statuses,
            Pageable pageable
    );

    @Query("""
            select p
            from ProjectEntity p
            where p.deletedAt is null
              and p.isPublic = true
              and (
                :query is null
                or lower(p.code) like lower(concat('%', :query, '%'))
                or lower(p.name) like lower(concat('%', :query, '%'))
                or lower(coalesce(p.description, '')) like lower(concat('%', :query, '%'))
                or lower(coalesce(p.goal, '')) like lower(concat('%', :query, '%'))
              )
              and (:projectType is null or p.projectType = :projectType)
              and p.status in :statuses
              and (:recruitingOnly = false or (coalesce(p.isRecruiting, false) = true and p.status in :recruitableStatuses))
              and (:excludeEffectiveRecruiting = false or not (coalesce(p.isRecruiting, false) = true and p.status in :recruitableStatuses))
              and (
                :researchFieldId is null
                or exists (
                    select prfById.id
                    from ProjectResearchFieldEntity prfById
                    where prfById.project.id = p.id
                      and prfById.researchField.id = :researchFieldId
                )
              )
              and (
                :researchFieldCode is null
                or exists (
                    select prfByCode.id
                    from ProjectResearchFieldEntity prfByCode
                    where prfByCode.project.id = p.id
                      and lower(prfByCode.researchField.code) = lower(:researchFieldCode)
                )
              )
            order by p.createdAt desc, p.id desc
            """)
    Page<ProjectEntity> findPublicProjects(
            @Param("query") String query,
            @Param("projectType") ProjectType projectType,
            @Param("statuses") List<ProjectStatus> statuses,
            @Param("recruitingOnly") boolean recruitingOnly,
            @Param("excludeEffectiveRecruiting") boolean excludeEffectiveRecruiting,
            @Param("recruitableStatuses") List<ProjectStatus> recruitableStatuses,
            @Param("researchFieldId") Long researchFieldId,
            @Param("researchFieldCode") String researchFieldCode,
            Pageable pageable
    );

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from ProjectEntity p
            where p.id = :id
              and p.deletedAt is null
            """)
    Optional<ProjectEntity> findActiveByIdForUpdate(@Param("id") Long id);

}
