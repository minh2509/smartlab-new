package com.smartlab.repo;

import com.smartlab.entity.DocumentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, Long>, JpaSpecificationExecutor<DocumentEntity> {
    @Override
    @EntityGraph(attributePaths = {"currentFile", "project"})
    Page<DocumentEntity> findAll(Specification<DocumentEntity> specification, Pageable pageable);

    @Query("""
            select distinct extract(year from d.updatedAt)
            from DocumentEntity d
            join d.currentFile f
            join d.project p
            where d.deletedAt is null
              and f.deletedAt is null
              and f.accessScope = 'PUBLIC'
              and p.deletedAt is null
              and p.isPublic = true
              and d.updatedAt is not null
            order by extract(year from d.updatedAt) desc
            """)
    List<Integer> findPublicYears();
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
