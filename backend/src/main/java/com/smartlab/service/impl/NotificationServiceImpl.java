package com.smartlab.service.impl;

import com.smartlab.entity.NotificationEntity;
import com.smartlab.dto.response.NotificationResponse;
import com.smartlab.repo.NotificationRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.entity.UserEntity;
import com.smartlab.service.NotificationRelated;
import com.smartlab.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void notify(Long recipientUserId, String type, String message, NotificationRelated related, Instant createdAt) {
        NotificationRelated metadata = related == null ? new NotificationRelated(null, null, null, null) : related;
        notificationRepository.saveAndFlush(NotificationEntity.create(
                recipientUserId, metadata.actorUserId(), type, message, metadata.relatedType(),
                metadata.relatedId(), metadata.targetUrl(), createdAt));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(String authenticatedEmail) {
        Long recipientId = resolveActiveUser(authenticatedEmail).getId();
        return notificationRepository.findByRecipientUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(recipientId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void markRead(String authenticatedEmail, Long notificationId) {
        NotificationEntity notification = findOwnedActive(notificationId, resolveActiveUser(authenticatedEmail).getId());
        notification.markRead();
    }

    @Override
    @Transactional
    public void markAllRead(String authenticatedEmail) {
        notificationRepository.markAllReadByRecipientUserId(resolveActiveUser(authenticatedEmail).getId());
    }

    @Override
    @Transactional
    public void softDelete(String authenticatedEmail, Long notificationId) {
        NotificationEntity notification = findOwnedActive(notificationId, resolveActiveUser(authenticatedEmail).getId());
        notification.softDelete(Instant.now());
    }

    private UserEntity resolveActiveUser(String authenticatedEmail) {
        return userRepository.findByEmail(authenticatedEmail)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is unavailable"));
    }

    private NotificationEntity findOwnedActive(Long id, Long recipientId) {
        return notificationRepository.findByIdAndRecipientUserIdAndDeletedAtIsNull(id, recipientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    private NotificationResponse toResponse(NotificationEntity notification) {
        return new NotificationResponse(notification.getId(), notification.getActorUserId(), notification.getType(),
                notification.getMessage(), notification.getRelatedType(), notification.getRelatedId(),
                notification.getTargetUrl(), notification.isRead(), notification.getCreatedAt());
    }
}
