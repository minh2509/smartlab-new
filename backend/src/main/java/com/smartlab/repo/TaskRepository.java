package com.smartlab.repo;

import com.smartlab.entity.TaskEntity;
import com.smartlab.enums.TaskPriority;
import com.smartlab.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, Long> {

    Optional<TaskEntity> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
            select t from TaskEntity t
            where t.project.id = :projectId
              and t.deletedAt is null
              and (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
              and (:assigneeUserId is null or exists (
                  select 1 from TaskAssigneeEntity ta
                  where ta.task.id = t.id and ta.user.id = :assigneeUserId
              ))
            order by t.createdAt desc
            """)
    Page<TaskEntity> findByProjectFiltered(
            @Param("projectId") Long projectId,
            @Param("status") TaskStatus status,
            @Param("priority") TaskPriority priority,
            @Param("assigneeUserId") Long assigneeUserId,
            Pageable pageable
    );
}
