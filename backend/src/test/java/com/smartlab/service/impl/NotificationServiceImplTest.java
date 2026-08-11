package com.smartlab.service.impl;

import com.smartlab.entity.NotificationEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.repo.NotificationRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.NotificationRelated;
import com.smartlab.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {
    @Mock NotificationRepository notifications;
    @Mock UserRepository users;
    NotificationService service;
    private final Instant time = Instant.parse("2026-08-10T10:00:00Z");
    @BeforeEach void setUp() { service = new NotificationServiceImpl(notifications, users); }
    @Test void notifyBuildsExactEntityAndFlushes() {
        service.notify(1L, "TYPE", "message", new NotificationRelated(2L, "POST", 3L, "/p/3"), time);
        ArgumentCaptor<NotificationEntity> c = ArgumentCaptor.forClass(NotificationEntity.class); verify(notifications).saveAndFlush(c.capture());
        assertThat(c.getValue().getActorUserId()).isEqualTo(2L); assertThat(c.getValue().getType()).isEqualTo("TYPE"); assertThat(c.getValue().getRelatedId()).isEqualTo(3L);
    }
    @Test void notifyPropagatesFailure() { RuntimeException failure = new RuntimeException(); when(notifications.saveAndFlush(any())).thenThrow(failure); assertThatThrownBy(() -> service.notify(1L,"T","m",null,time)).isSameAs(failure); }
    @Test void listResolvesActiveCanonicalUserAndFiltersOwnActive() {
        UserEntity user = active(1L); when(users.findByEmail("a@b")).thenReturn(Optional.of(user));
        NotificationEntity n = NotificationEntity.create(1L,null,"T","m",null,null,null,time); when(notifications.findByRecipientUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(1L)).thenReturn(List.of(n));
        assertThat(service.getNotifications("a@b")).hasSize(1); verify(notifications).findByRecipientUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(1L);
    }
    @Test void listBatchResolvesDistinctActorNamesIncludingInactiveAccounts() {
        UserEntity recipient = active(1L);
        UserEntity inactiveActor = UserEntity.builder().id(2L).name("Reviewer Name").isActive(false).build();
        NotificationEntity first = NotificationEntity.create(1L,2L,"T1","m1",null,null,null,time);
        NotificationEntity second = NotificationEntity.create(1L,2L,"T2","m2",null,null,null,time.minusSeconds(1));
        NotificationEntity actorless = NotificationEntity.create(1L,null,"T3","m3",null,null,null,time.minusSeconds(2));
        when(users.findByEmail("a@b")).thenReturn(Optional.of(recipient));
        when(notifications.findByRecipientUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(1L))
                .thenReturn(List.of(first, second, actorless));
        when(users.findAllById(any())).thenReturn(List.of(inactiveActor));

        var responses = service.getNotifications("a@b");

        assertThat(responses).extracting(response -> response.actorName())
                .containsExactly("Reviewer Name", "Reviewer Name", null);
        verify(users).findAllById(argThat(ids -> {
            assertThat(ids).containsExactly(2L);
            return true;
        }));
    }
    @Test void unavailableCanonicalUserIsUnauthorized() { when(users.findByEmail("x")).thenReturn(Optional.empty()); assertThatThrownBy(() -> service.getNotifications("x")).isInstanceOf(ResponseStatusException.class).extracting(e -> ((ResponseStatusException)e).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED); }
    @Test void markAndDeleteAreRecipientScopedAndMissingRowsAreNotFound() {
        when(users.findByEmail("a")).thenReturn(Optional.of(active(1L))); when(notifications.findByIdAndRecipientUserIdAndDeletedAtIsNull(2L,1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markRead("a",2L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.softDelete("a",2L)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void markAllUsesOnlyCanonicalRecipient() { when(users.findByEmail("a")).thenReturn(Optional.of(active(1L))); service.markAllRead("a"); verify(notifications).markAllReadByRecipientUserId(1L); }
    private static UserEntity active(Long id) { return UserEntity.builder().id(id).isActive(true).email("a").build(); }
}
