package com.smartlab.service.impl;

import com.smartlab.entity.NotificationEntity;
import com.smartlab.repo.NotificationRepository;
import com.smartlab.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final Long RECIPIENT_ID = 18L;
    private static final Instant CREATED_AT = Instant.parse("2026-08-10T10:00:00Z");

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(notificationRepository);
    }

    @Test
    void recordsExactUnreadNotificationThroughFlushPersistence() {
        String message = "  Your notification  ";
        String linkUrl = "  /posts/example  ";

        notificationService.recordNotification(RECIPIENT_ID, message, linkUrl, CREATED_AT);

        ArgumentCaptor<NotificationEntity> notificationCaptor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).saveAndFlush(notificationCaptor.capture());
        NotificationEntity notification = notificationCaptor.getValue();
        assertThat(notification.getRecipientUserId()).isEqualTo(RECIPIENT_ID);
        assertThat(notification.getMessage()).isEqualTo(message);
        assertThat(notification.getLinkUrl()).isEqualTo(linkUrl);
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void recordsNotificationWithNullableLink() {
        notificationService.recordNotification(RECIPIENT_ID, "message", null, CREATED_AT);

        ArgumentCaptor<NotificationEntity> notificationCaptor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).saveAndFlush(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getLinkUrl()).isNull();
    }

    @Test
    void propagatesPersistenceFailureUnchanged() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("persistence failure");
        when(notificationRepository.saveAndFlush(any(NotificationEntity.class))).thenThrow(failure);

        assertThatThrownBy(() -> notificationService.recordNotification(RECIPIENT_ID, "message", null, CREATED_AT))
                .isSameAs(failure);

        verify(notificationRepository).saveAndFlush(any(NotificationEntity.class));
    }

    @Test
    void rejectsInvalidInputBeforeRepositoryPersistence() {
        assertThatThrownBy(() -> notificationService.recordNotification(0L, "message", null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification recipient user ID must be positive");
        assertThatThrownBy(() -> notificationService.recordNotification(RECIPIENT_ID, null, null, CREATED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Notification message is required");
        assertThatThrownBy(() -> notificationService.recordNotification(RECIPIENT_ID, "message", null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Notification creation instant is required");

        verifyNoInteractions(notificationRepository);
    }

    @Test
    void usesDefaultRequiredTransactionPropagation() throws NoSuchMethodException {
        Method method = NotificationServiceImpl.class.getMethod(
                "recordNotification",
                Long.class,
                String.class,
                String.class,
                Instant.class
        );

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(transactional.readOnly()).isFalse();
    }
}
