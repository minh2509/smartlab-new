package com.smartlab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "notifications")
public class NotificationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "type", nullable = false, length = 100)
    private String type;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "related_type", length = 80)
    private String relatedType;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "target_url", length = 500)
    private String targetUrl;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static NotificationEntity create(
            Long recipientUserId,
            Long actorUserId,
            String type,
            String message,
            String relatedType,
            Long relatedId,
            String targetUrl,
            Instant createdAt
    ) {
        requirePositiveRecipientId(recipientUserId);
        requirePositiveOptionalId(actorUserId, "Notification actor user ID");
        Objects.requireNonNull(type, "Notification type is required");
        Objects.requireNonNull(message, "Notification message is required");
        Objects.requireNonNull(createdAt, "Notification creation instant is required");
        requireNonBlank(type, "Notification type must not be blank");
        requireNonBlank(message, "Notification message must not be blank");
        validateLength(type, 100, "Notification type must be at most 100 characters");
        validateLength(message, 1000, "Notification message must be at most 1000 characters");
        validateOptionalLength(relatedType, 80, "Notification related type must be at most 80 characters");
        requirePositiveOptionalId(relatedId, "Notification related ID");
        validateOptionalLength(targetUrl, 500, "Notification target URL must be at most 500 characters");

        NotificationEntity notification = new NotificationEntity();
        notification.recipientUserId = recipientUserId;
        notification.actorUserId = actorUserId;
        notification.type = type;
        notification.message = message;
        notification.relatedType = relatedType;
        notification.relatedId = relatedId;
        notification.targetUrl = targetUrl;
        notification.isRead = false;
        notification.deletedAt = null;
        notification.createdAt = createdAt;
        return notification;
    }

    public void markRead() {
        isRead = true;
    }

    public void softDelete(Instant deletedAt) {
        this.deletedAt = Objects.requireNonNull(deletedAt, "Notification deletion instant is required");
    }

    private static void requirePositiveRecipientId(Long recipientUserId) {
        Objects.requireNonNull(recipientUserId, "Notification recipient user ID is required");
        if (recipientUserId <= 0) {
            throw new IllegalArgumentException("Notification recipient user ID must be positive");
        }
    }

    private static void requirePositiveOptionalId(Long value, String label) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }

    private static void requireNonBlank(String value, String message) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void validateOptionalLength(String value, int maximumLength, String message) {
        if (value != null) {
            validateLength(value, maximumLength, message);
        }
    }

    private static void validateLength(String value, int maximumLength, String message) {
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(message);
        }
    }
}
