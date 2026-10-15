package com.smartlab.repo;

import com.smartlab.entity.StoredFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFileEntity, Long> {
    Optional<StoredFileEntity> findByIdAndDeletedAtIsNull(Long id);

    // A private upload must not be reclassified as CHAT while another domain owns it.
    @Query(value = """
            SELECT EXISTS (
              SELECT 1 FROM member_profiles WHERE avatar_file_id = :fileId
              UNION ALL SELECT 1 FROM research_fields WHERE cover_file_id = :fileId
              UNION ALL SELECT 1 FROM posts WHERE cover_file_id = :fileId
              UNION ALL SELECT 1 FROM documents WHERE current_file_id = :fileId
              UNION ALL SELECT 1 FROM document_versions WHERE file_id = :fileId
              UNION ALL SELECT 1 FROM task_attachments WHERE file_id = :fileId
              UNION ALL SELECT 1 FROM lab_achievement_files WHERE file_id = :fileId
              UNION ALL SELECT 1 FROM gallery_items WHERE file_id = :fileId
            )
            """, nativeQuery = true)
    boolean hasNonChatReference(@Param("fileId") Long fileId);

    List<StoredFileEntity> findAllByOwnerUser_IdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long ownerUserId);
}
