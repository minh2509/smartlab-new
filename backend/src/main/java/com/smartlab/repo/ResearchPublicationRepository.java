package com.smartlab.repo;

import com.smartlab.entity.ResearchPublicationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResearchPublicationRepository extends JpaRepository<ResearchPublicationEntity, Long> {
    @Query("""
            select p from ResearchPublicationEntity p where p.deletedAt is null and p.isPublic = true
            and p.publicationYear = :year order by p.publicationDate desc nulls last, p.createdAt desc, p.id desc
            """)
    Page<ResearchPublicationEntity> findPublicByYear(@Param("year") int year, Pageable pageable);

    @Query("""
            select p.publicationYear, count(p) from ResearchPublicationEntity p
            where p.deletedAt is null and p.isPublic = true group by p.publicationYear order by p.publicationYear desc
            """)
    List<Object[]> findPublicYearCounts();

    @Query("select max(p.publicationYear) from ResearchPublicationEntity p where p.deletedAt is null and p.isPublic = true")
    Integer findNewestPublicYear();

    Optional<ResearchPublicationEntity> findByIdAndDeletedAtIsNull(Long id);
}
