package com.smartlab.entity;

import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;
import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
        name = "project_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_members_project_user",
                columnNames = {"project_id", "user_id"}
        )
)
public class ProjectMemberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_role", nullable = false, length = 30)
    private ProjectRole projectRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectMemberStatus status;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Timestamp joinedAt;

    @Column(name = "removed_at")
    private Timestamp removedAt;

    public static ProjectMemberEntity createLeader(ProjectEntity project, UserEntity user) {
        return ProjectMemberEntity.builder()
                .project(project)
                .user(user)
                .projectRole(ProjectRole.LEADER)
                .status(ProjectMemberStatus.ACTIVE)
                .build();
    }

    public static ProjectMemberEntity createMember(ProjectEntity project, UserEntity user) {
        return ProjectMemberEntity.builder()
                .project(project)
                .user(user)
                .projectRole(ProjectRole.MEMBER)
                .status(ProjectMemberStatus.ACTIVE)
                .build();
    }

    public void activateAsLeader() {
        this.projectRole = ProjectRole.LEADER;
        this.status = ProjectMemberStatus.ACTIVE;
        this.removedAt = null;
    }

    public void activateAsMember() {
        this.projectRole = ProjectRole.MEMBER;
        this.status = ProjectMemberStatus.ACTIVE;
        this.removedAt = null;
    }

    public void remove() {
        this.status = ProjectMemberStatus.REMOVED;
        this.removedAt = Timestamp.from(Instant.now());
    }
}
