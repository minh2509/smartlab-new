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

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static NotificationEntity create(
            Long recipientUserId,
            String message,
            String linkUrl,
            Instant createdAt
    ) {
        requirePositiveRecipientId(recipientUserId);
        Objects.requireNonNull(message, "Notification message is required");
        Objects.requireNonNull(createdAt, "Notification creation instant is required");
        validateLength(message, 500, "Notification message must be at most 500 characters");
        if (linkUrl != null) {
            validateLength(linkUrl, 500, "Notification link URL must be at most 500 characters");
        }

        NotificationEntity notification = new NotificationEntity();
        notification.recipientUserId = recipientUserId;
        notification.message = message;
        notification.linkUrl = linkUrl;
        notification.isRead = false;
        notification.createdAt = createdAt;
        return notification;
    }

    private static void requirePositiveRecipientId(Long recipientUserId) {
        Objects.requireNonNull(recipientUserId, "Notification recipient user ID is required");
        if (recipientUserId <= 0) {
            throw new IllegalArgumentException("Notification recipient user ID must be positive");
        }
    }

    private static void validateLength(String value, int maximumLength, String message) {
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(message);
        }
    }
}
