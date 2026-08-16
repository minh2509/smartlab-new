package com.smartlab.entity;

import com.smartlab.enums.EventMode;
import com.smartlab.enums.EventStatus;
import com.smartlab.enums.EventVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "events")
public class EventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
    private EventMode mode;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "meeting_url", length = 2048)
    private String meetingUrl;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EventStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private EventVisibility visibility;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static EventEntity create(
            Long projectId,
            String title,
            String content,
            EventMode mode,
            String location,
            String meetingUrl,
            Instant startAt,
            Instant endAt,
            EventStatus status,
            EventVisibility visibility,
            Long createdByUserId,
            Instant creationTime
    ) {
        validateState(projectId, title, content, mode, location, meetingUrl,
                startAt, endAt, status, visibility, creationTime);
        Objects.requireNonNull(createdByUserId, "Event creator is required");

        EventEntity event = new EventEntity();
        event.projectId = projectId;
        event.title = title;
        event.content = content;
        event.mode = mode;
        event.location = location;
        event.meetingUrl = meetingUrl;
        event.startAt = startAt;
        event.endAt = endAt;
        event.status = status;
        event.visibility = visibility;
        event.createdByUserId = createdByUserId;
        event.createdAt = creationTime;
        event.updatedAt = creationTime;
        return event;
    }

    public void applyUpdate(
            String title,
            String content,
            EventMode mode,
            String location,
            String meetingUrl,
            Instant startAt,
            Instant endAt,
            EventStatus status,
            EventVisibility visibility,
            Instant mutationTime
    ) {
        validateState(projectId, title, content, mode, location, meetingUrl,
                startAt, endAt, status, visibility, mutationTime);
        this.title = title;
        this.content = content;
        this.mode = mode;
        this.location = location;
        this.meetingUrl = meetingUrl;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = status;
        this.visibility = visibility;
        this.updatedAt = mutationTime;
    }

    public void softDelete(Instant mutationTime) {
        Objects.requireNonNull(mutationTime, "Mutation time is required");
        this.deletedAt = mutationTime;
        this.updatedAt = mutationTime;
    }

    private static void validateState(
            Long projectId,
            String title,
            String content,
            EventMode mode,
            String location,
            String meetingUrl,
            Instant startAt,
            Instant endAt,
            EventStatus status,
            EventVisibility visibility,
            Instant mutationTime
    ) {
        Objects.requireNonNull(title, "Event title is required");
        Objects.requireNonNull(mode, "Event mode is required");
        Objects.requireNonNull(startAt, "Event start time is required");
        Objects.requireNonNull(status, "Event status is required");
        Objects.requireNonNull(visibility, "Event visibility is required");
        Objects.requireNonNull(mutationTime, "Mutation time is required");
        if (endAt != null && !endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Event end time must be after start time");
        }
        if (mode == EventMode.IN_PERSON && (location == null || meetingUrl != null)) {
            throw new IllegalArgumentException("In-person events require a location and no meeting URL");
        }
        if (mode == EventMode.ONLINE && (meetingUrl == null || location != null)) {
            throw new IllegalArgumentException("Online events require a meeting URL and no location");
        }
        if (visibility == EventVisibility.PROJECT && projectId == null) {
            throw new IllegalArgumentException("Event visibility and project association are inconsistent");
        }
    }
}
