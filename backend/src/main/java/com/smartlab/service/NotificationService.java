package com.smartlab.service;

import java.time.Instant;

public interface NotificationService {
    void recordNotification(Long recipientUserId, String message, String linkUrl, Instant createdAt);
}
