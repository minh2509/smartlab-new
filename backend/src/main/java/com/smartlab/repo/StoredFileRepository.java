package com.smartlab.repo;

import com.smartlab.entity.StoredFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFileEntity, Long> {
    Optional<StoredFileEntity> findByIdAndDeletedAtIsNull(Long id);

    List<StoredFileEntity> findAllByOwnerUser_IdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long ownerUserId);
}
