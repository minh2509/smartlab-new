package com.smartlab.repo;

import com.smartlab.entity.ProjectEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
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
