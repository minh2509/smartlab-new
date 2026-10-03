package com.smartlab.repo;

import com.smartlab.entity.DocumentCategoryEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentCategoryRepository extends JpaRepository<DocumentCategoryEntity, Long> {
    List<DocumentCategoryEntity> findAllByOrderByDisplayOrderAscNameAscIdAsc();

    List<DocumentCategoryEntity> findAllByIsActiveTrueOrderByDisplayOrderAscNameAscIdAsc();

    Optional<DocumentCategoryEntity> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query("""
            select c.id as categoryId, count(d.id) as documentCount
            from DocumentCategoryEntity c
            left join DocumentEntity d on d.category = c
                and d.deletedAt is null
                and d.currentFile.deletedAt is null
                and d.currentFile.accessScope = 'PUBLIC'
                and d.project.deletedAt is null
                and d.project.isPublic = true
                and (:year is null or extract(year from d.archiveDate) = :year)
            where c.isActive = true
            group by c.id
            """)
    List<CategoryCountProjection> countPublicDocumentsByCategory(@Param("year") Integer year);

    @Query("""
            select c.id as categoryId, count(d.id) as documentCount
            from DocumentCategoryEntity c
            left join DocumentEntity d on d.category = c
                and d.deletedAt is null
                and d.currentFile.deletedAt is null
                and d.project.deletedAt is null
            group by c.id
            """)
    List<CategoryCountProjection> countTotalDocumentsByCategory();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DocumentCategoryEntity c order by c.id")
    List<DocumentCategoryEntity> findAllForUpdate();

    interface CategoryCountProjection {
        Long getCategoryId();
        long getDocumentCount();
    }
}
