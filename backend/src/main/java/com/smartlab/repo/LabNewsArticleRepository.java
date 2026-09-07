package com.smartlab.repo;

import com.smartlab.entity.LabNewsArticleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LabNewsArticleRepository extends JpaRepository<LabNewsArticleEntity, Long> {
    @Query("""
            select article from LabNewsArticleEntity article
            where article.deletedAt is null and article.isPublic = true
            order by article.publishedAt desc, article.createdAt desc, article.id desc
            """)
    List<LabNewsArticleEntity> findNewestPublic(Pageable pageable);

    @Query("""
            select article from LabNewsArticleEntity article
            where article.deletedAt is null and article.isPublic = true
            order by article.publishedAt desc, article.createdAt desc, article.id desc
            """)
    Page<LabNewsArticleEntity> findPublicArchive(Pageable pageable);

    @Query("""
            select article from LabNewsArticleEntity article
            where article.deletedAt is null
            order by article.publishedAt desc, article.createdAt desc, article.id desc
            """)
    Page<LabNewsArticleEntity> findActiveForAdmin(Pageable pageable);

    Optional<LabNewsArticleEntity> findByIdAndDeletedAtIsNull(Long id);
}
