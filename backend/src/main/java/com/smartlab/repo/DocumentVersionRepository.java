package com.smartlab.repo;

import com.smartlab.entity.DocumentVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersionEntity, Long> {
    @Query("""
            select dv
            from DocumentVersionEntity dv
            join fetch dv.file f
            left join fetch dv.uploadedBy
            where dv.document.id = :documentId
              and f.deletedAt is null
            order by dv.versionNo desc
            """)
    List<DocumentVersionEntity> findReadableCandidatesByDocumentId(@Param("documentId") Long documentId);

    @Query("""
            select coalesce(max(dv.versionNo), 0)
            from DocumentVersionEntity dv
            where dv.document.id = :documentId
            """)
    int findMaxVersionNo(@Param("documentId") Long documentId);

    boolean existsByFile_Id(Long fileId);
}
