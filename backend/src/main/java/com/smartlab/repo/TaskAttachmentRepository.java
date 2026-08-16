package com.smartlab.repo;

import com.smartlab.entity.TaskAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskAttachmentRepository extends JpaRepository<TaskAttachmentEntity, Long> {

    @Query("""
            select ta from TaskAttachmentEntity ta
            join fetch ta.file
            where ta.task.id = :taskId
            order by ta.createdAt desc
            """)
    List<TaskAttachmentEntity> findAllByTaskId(@Param("taskId") Long taskId);
}
