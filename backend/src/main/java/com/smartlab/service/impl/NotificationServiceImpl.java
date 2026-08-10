package com.smartlab.service.impl;

import com.smartlab.entity.NotificationEntity;
import com.smartlab.repo.NotificationRepository;
import com.smartlab.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void recordNotification(Long recipientUserId, String message, String linkUrl, Instant createdAt) {
        notificationRepository.saveAndFlush(NotificationEntity.create(recipientUserId, message, linkUrl, createdAt));
    }
}
