package com.smartlab.service;

import java.time.Instant;
import java.util.List;
import com.smartlab.dto.response.NotificationResponse;

public interface NotificationService {
    void notify(Long recipientUserId, String type, String message, NotificationRelated related, Instant createdAt);
    List<NotificationResponse> getNotifications(String authenticatedEmail);
    void markRead(String authenticatedEmail, Long notificationId);
    void markAllRead(String authenticatedEmail);
    void softDelete(String authenticatedEmail, Long notificationId);
}
