package com.smartlab.repo;

import com.smartlab.entity.DocumentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
    @Query("""
            select d
            from DocumentEntity d
            join fetch d.currentFile f
            left join fetch d.createdBy
            where d.project.id = :projectId
              and d.deletedAt is null
              and f.deletedAt is null
            order by d.updatedAt desc, d.id desc
            """)
    List<DocumentEntity> findActiveByProjectId(@Param("projectId") Long projectId);

    @Query("""
            select d
            from DocumentEntity d
            join fetch d.currentFile f
            left join fetch d.createdBy
            where d.id = :id
              and d.deletedAt is null
              and d.project.deletedAt is null
              and f.deletedAt is null
            """)
    Optional<DocumentEntity> findActiveById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d
            from DocumentEntity d
            where d.id = :id
              and d.deletedAt is null
              and d.project.deletedAt is null
            """)
    Optional<DocumentEntity> findActiveByIdForUpdate(@Param("id") Long id);

    boolean existsByCurrentFile_IdAndDeletedAtIsNull(Long fileId);
}
