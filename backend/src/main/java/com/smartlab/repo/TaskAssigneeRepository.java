package com.smartlab.repo;

import com.smartlab.entity.TaskAssigneeEntity;
import com.smartlab.entity.TaskAssigneeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskAssigneeRepository extends JpaRepository<TaskAssigneeEntity, TaskAssigneeId> {

    List<TaskAssigneeEntity> findAllByTask_Id(Long taskId);

    boolean existsById_TaskIdAndId_UserId(Long taskId, Long userId);

    void deleteById_TaskIdAndId_UserId(Long taskId, Long userId);

    @Query("""
            select ta from TaskAssigneeEntity ta
            join fetch ta.user
            where ta.task.id in :taskIds
            """)
    List<TaskAssigneeEntity> findAllByTaskIds(@Param("taskIds") List<Long> taskIds);
}
