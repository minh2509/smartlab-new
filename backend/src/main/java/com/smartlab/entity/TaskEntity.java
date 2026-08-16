package com.smartlab.entity;

import com.smartlab.enums.TaskPriority;
import com.smartlab.enums.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "tasks")
public class TaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id")
    private TaskEntity parentTask;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private UserEntity createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    public static TaskEntity create(
            ProjectEntity project,
            TaskEntity parentTask,
            String title,
            String description,
            TaskStatus status,
            TaskPriority priority,
            Instant startAt,
            Instant dueAt,
            UserEntity createdBy
    ) {
        return TaskEntity.builder()
                .project(project)
                .parentTask(parentTask)
                .title(title)
                .description(description)
                .status(status)
                .priority(priority)
                .startAt(startAt)
                .dueAt(dueAt)
                .createdBy(createdBy)
                .build();
    }

    public void update(
            String title,
            String description,
            TaskStatus status,
            TaskPriority priority,
            Instant startAt,
            Instant dueAt,
            TaskEntity parentTask
    ) {
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.startAt = startAt;
        this.dueAt = dueAt;
        this.parentTask = parentTask;
    }

    public void transitionStatus(TaskStatus newStatus) {
        this.status = newStatus;
    }

    public void softDelete() {
        this.deletedAt = Timestamp.from(Instant.now());
    }
}
