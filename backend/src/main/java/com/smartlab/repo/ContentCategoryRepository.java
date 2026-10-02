package com.smartlab.repo;

import com.smartlab.entity.ContentCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContentCategoryRepository extends JpaRepository<ContentCategoryEntity, Long> {
    List<ContentCategoryEntity> findByIsActiveTrueOrderByIdAsc();

    List<ContentCategoryEntity> findAllByOrderByIdAsc();

    boolean existsByCodeIgnoreCase(String code);

    Optional<ContentCategoryEntity> findByIdAndIsActiveTrue(Long id);
}
