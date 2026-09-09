package com.smartlab.repo;

import com.smartlab.entity.LabArticleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LabArticleRepository extends JpaRepository<LabArticleEntity, Long> {
    @Query("""
            select article from LabArticleEntity article
            where article.deletedAt is null and article.status = com.smartlab.enums.LabArticleStatus.PUBLISHED
            order by article.publishedAt desc, article.createdAt desc, article.id desc
            """)
    List<LabArticleEntity> findNewestPublished(Pageable pageable);

    @Query("""
            select article from LabArticleEntity article
            where article.deletedAt is null
              and article.status = com.smartlab.enums.LabArticleStatus.PUBLISHED
              and (:q = '' or lower(article.title) like lower(concat('%', :q, '%'))
                   or lower(coalesce(article.excerpt, '')) like lower(concat('%', :q, '%')))
              and (:year = 0 or year(article.publishedAt) = :year)
            """)
    Page<LabArticleEntity> findPublishedArchive(String q, Integer year, Pageable pageable);

    @Query("""
            select distinct year(article.publishedAt) from LabArticleEntity article
            where article.deletedAt is null
              and article.status = com.smartlab.enums.LabArticleStatus.PUBLISHED
              and article.publishedAt is not null
            order by year(article.publishedAt) desc
            """)
    List<Integer> findPublishedYears();

    @Query("""
            select article from LabArticleEntity article
            where article.deletedAt is null
            order by article.updatedAt desc, article.createdAt desc, article.id desc
            """)
    Page<LabArticleEntity> findActiveForAdmin(Pageable pageable);

    Optional<LabArticleEntity> findBySlugAndStatusAndDeletedAtIsNull(String slug, com.smartlab.enums.LabArticleStatus status);
    Optional<LabArticleEntity> findByIdAndDeletedAtIsNull(Long id);
    boolean existsBySlugAndDeletedAtIsNull(String slug);
}
