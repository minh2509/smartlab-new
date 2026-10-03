package com.smartlab.repo;

import com.smartlab.entity.DocumentVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    @Query("""
            select dv.document.id as documentId, max(dv.versionNo) as maxVersionNo
            from DocumentVersionEntity dv
            where dv.document.id in :documentIds
            group by dv.document.id
            """)
    List<VersionNumberProjection> findMaxVersionNosByDocumentIds(@Param("documentIds") Collection<Long> documentIds);

    interface VersionNumberProjection {
        Long getDocumentId();
        Integer getMaxVersionNo();
    }

    boolean existsByFile_Id(Long fileId);

    @Query("""
            select case when count(dv) > 0 then true else false end
            from DocumentVersionEntity dv
            where dv.file.id = :fileId
              and dv.document.id <> :documentId
              and dv.document.deletedAt is null
              and dv.document.project.deletedAt is null
              and dv.file.deletedAt is null
            """)
    boolean existsActiveReferenceOutsideDocument(
            @Param("fileId") Long fileId,
            @Param("documentId") Long documentId
    );
}
