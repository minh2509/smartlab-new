package com.smartlab.repo;

import com.smartlab.entity.GalleryItemEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface GalleryItemRepository extends JpaRepository<GalleryItemEntity, Long>, JpaSpecificationExecutor<GalleryItemEntity> {
    @Override
    @EntityGraph(attributePaths = {"file", "project", "event"})
    Page<GalleryItemEntity> findAll(Specification<GalleryItemEntity> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"file", "project", "event"})
    @Query("select g from GalleryItemEntity g where g.id = :id and g.deletedAt is null")
    Optional<GalleryItemEntity> findActiveById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GalleryItemEntity g where g.id = :id and g.deletedAt is null")
    Optional<GalleryItemEntity> findActiveByIdForUpdate(@Param("id") Long id);

    @Query("select distinct extract(year from coalesce(g.capturedAt, g.publishedAt)) from GalleryItemEntity g join g.file f left join g.project p left join g.event e where g.deletedAt is null and g.status = com.smartlab.enums.GalleryItemStatus.PUBLISHED and f.deletedAt is null and f.accessScope = 'PUBLIC' and (p.id is null or (p.deletedAt is null and p.isPublic = true)) and (e.id is null or (e.deletedAt is null and e.visibility = com.smartlab.enums.EventVisibility.PUBLIC)) order by extract(year from coalesce(g.capturedAt, g.publishedAt)) desc")
    List<Integer> findPublicYears();

    boolean existsByFile_IdAndDeletedAtIsNull(Long fileId);
}
