package com.smartlab.repo;

import com.smartlab.entity.ProjectEntity;
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
              and p.isRecruiting = true
              and p.status in :statuses
            order by p.createdAt desc, p.id desc
            """)
    Page<ProjectEntity> findPublicRecruitingProjects(
            @Param("statuses") List<com.smartlab.enums.ProjectStatus> statuses,
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
