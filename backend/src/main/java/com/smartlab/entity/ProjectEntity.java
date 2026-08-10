package com.smartlab.entity;

import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
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
import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "projects")
public class ProjectEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 20)
    private ProjectType projectType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_user_id")
    private UserEntity leader;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProjectStatus status;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "expected_end_date")
    private LocalDate expectedEndDate;

    @Column(name = "actual_end_date")
    private LocalDate actualEndDate;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic;

    @Column(name = "is_featured", nullable = false)
    private Boolean isFeatured;

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

    public static ProjectEntity create(
            String code,
            String name,
            String description,
            String goal,
            ProjectType projectType,
            UserEntity leader,
            ProjectStatus status,
            LocalDate startDate,
            LocalDate expectedEndDate,
            LocalDate actualEndDate,
            Boolean isPublic,
            Boolean isFeatured,
            UserEntity createdBy
    ) {
        return ProjectEntity.builder()
                .code(code)
                .name(name)
                .description(description)
                .goal(goal)
                .projectType(projectType)
                .leader(leader)
                .status(status)
                .startDate(startDate)
                .expectedEndDate(expectedEndDate)
                .actualEndDate(actualEndDate)
                .isPublic(isPublic)
                .isFeatured(isFeatured)
                .createdBy(createdBy)
                .build();
    }

    public void updateCore(
            String code,
            String name,
            String description,
            String goal,
            ProjectType projectType,
            ProjectStatus status,
            LocalDate startDate,
            LocalDate expectedEndDate,
            LocalDate actualEndDate,
            Boolean isPublic,
            Boolean isFeatured
    ) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.goal = goal;
        this.projectType = projectType;
        this.status = status;
        this.startDate = startDate;
        this.expectedEndDate = expectedEndDate;
        this.actualEndDate = actualEndDate;
        this.isPublic = isPublic;
        this.isFeatured = isFeatured;
    }

    public void changeLeader(UserEntity leader) {
        this.leader = leader;
    }

    public void softDelete() {
        this.deletedAt = Timestamp.from(Instant.now());
    }
}
