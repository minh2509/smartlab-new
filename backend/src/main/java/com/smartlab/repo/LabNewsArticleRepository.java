package com.smartlab.repo;

import com.smartlab.entity.LabNewsArticleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LabNewsArticleRepository extends JpaRepository<LabNewsArticleEntity, Long>, JpaSpecificationExecutor<LabNewsArticleEntity> {
    @Query("""
            select article from LabNewsArticleEntity article
            where article.deletedAt is null and article.isPublic = true
            order by article.publishedAt desc, article.createdAt desc, article.id desc
            """)
    List<LabNewsArticleEntity> findNewestPublic(Pageable pageable);

    @Query("""
            select article from LabNewsArticleEntity article
            where article.deletedAt is null and article.isPublic = true
            order by article.publishedAt desc, article.id desc
            """)
    Page<LabNewsArticleEntity> findPublicArchive(Pageable pageable);

    @Query("""
            select distinct trim(article.sourceName) from LabNewsArticleEntity article
            where article.deletedAt is null and article.isPublic = true
              and article.sourceName is not null and trim(article.sourceName) <> ''
            order by trim(article.sourceName)
            """)
    List<String> findPublicSources();

    @Query("""
            select distinct year(article.publishedAt) from LabNewsArticleEntity article
            where article.deletedAt is null and article.isPublic = true
              and article.publishedAt is not null
            order by year(article.publishedAt) desc
            """)
    List<Integer> findPublicYears();

    @Query("""
            select article from LabNewsArticleEntity article
            where article.deletedAt is null
            order by article.publishedAt desc, article.createdAt desc, article.id desc
            """)
    Page<LabNewsArticleEntity> findActiveForAdmin(Pageable pageable);

    Optional<LabNewsArticleEntity> findByIdAndDeletedAtIsNull(Long id);
}
