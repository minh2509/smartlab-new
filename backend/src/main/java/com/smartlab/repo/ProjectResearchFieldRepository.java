package com.smartlab.repo;

import com.smartlab.entity.ProjectResearchFieldEntity;
import com.smartlab.entity.ProjectResearchFieldId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectResearchFieldRepository
        extends JpaRepository<ProjectResearchFieldEntity, ProjectResearchFieldId> {

    @Query("""
            select prf
            from ProjectResearchFieldEntity prf
            join fetch prf.researchField rf
            where prf.project.id = :projectId
            order by lower(rf.name), lower(rf.code), rf.id
            """)
    List<ProjectResearchFieldEntity> findAllWithFieldByProjectId(@Param("projectId") Long projectId);

    @Query("""
            select prf.project.id
            from ProjectResearchFieldEntity prf
            where prf.researchField.id = :fieldId
              and prf.project.deletedAt is null
            order by prf.project.id
            """)
    List<Long> findActiveProjectIdsByFieldId(@Param("fieldId") Long fieldId);
}
