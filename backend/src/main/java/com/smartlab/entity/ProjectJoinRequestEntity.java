package com.smartlab.entity;

import com.smartlab.enums.ProjectJoinRequestStatus;
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

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "project_join_requests")
public class ProjectJoinRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_user_id", nullable = false)
    private UserEntity requester;

    @Column(length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectJoinRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private UserEntity reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ProjectJoinRequestEntity create(ProjectEntity project, UserEntity requester, String message) {
        return ProjectJoinRequestEntity.builder()
                .project(project)
                .requester(requester)
                .message(message)
                .status(ProjectJoinRequestStatus.PENDING)
                .build();
    }

    public void approve(UserEntity reviewer, Instant reviewedAt) {
        requirePending();
        status = ProjectJoinRequestStatus.APPROVED;
        reviewedBy = reviewer;
        this.reviewedAt = reviewedAt;
    }

    public void reject(UserEntity reviewer, Instant reviewedAt) {
        requirePending();
        status = ProjectJoinRequestStatus.REJECTED;
        reviewedBy = reviewer;
        this.reviewedAt = reviewedAt;
    }

    public void cancel() {
        requirePending();
        status = ProjectJoinRequestStatus.CANCELLED;
        reviewedBy = null;
        reviewedAt = null;
    }

    private void requirePending() {
        if (status != ProjectJoinRequestStatus.PENDING) {
            throw new IllegalStateException("Only a pending join request may be changed");
        }
    }
}
