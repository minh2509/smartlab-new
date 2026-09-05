package com.smartlab.repo;

import com.smartlab.entity.ResearchFieldEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResearchFieldRepository extends JpaRepository<ResearchFieldEntity, Long> {
    List<ResearchFieldEntity> findByIsActiveTrueOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCoverFile_Id(Long fileId);
}
