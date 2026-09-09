package com.smartlab.repo;

import com.smartlab.entity.ResearchFieldEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResearchFieldRepository extends JpaRepository<ResearchFieldEntity, Long> {
    List<ResearchFieldEntity> findByIsActiveTrueOrderByNameAsc();

    Optional<ResearchFieldEntity> findByCodeIgnoreCaseAndIsActiveTrue(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCoverFile_Id(Long fileId);
}
