package com.smartlab.service.impl;

import com.smartlab.entity.AccountInvitationBatchEntity;
import com.smartlab.entity.AccountInvitationItemEntity;
import com.smartlab.entity.EmailOutboxEntity;
import com.smartlab.entity.AccountInvitationEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.InvitationStatus;
import com.smartlab.enums.BulkInvitationBatchStatus;
import com.smartlab.enums.BulkInvitationItemStatus;
import com.smartlab.enums.EmailOutboxStatus;
import com.smartlab.repo.AccountInvitationItemRepository;
import com.smartlab.repo.AccountInvitationRepository;
import com.smartlab.repo.EmailOutboxRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.TokenHashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountInvitationOutboxStateServiceTest {
    @Mock private AccountInvitationRepository invitationRepository;
    @Mock private EmailOutboxRepository outboxRepository;
    @Mock private AccountInvitationItemRepository itemRepository;
    @Mock private UserRepository userRepository;
    @Mock private TokenHashService tokenHashService;

    private AccountInvitationOutboxStateService service;

    @BeforeEach
    void setUp() {
        service = new AccountInvitationOutboxStateService(outboxRepository, itemRepository, userRepository, tokenHashService, invitationRepository);
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        ReflectionTestUtils.setField(service, "processingTimeoutSeconds", 900L);
    }

    @Test
    void requeuesStaleProcessingMessageBeforeClaimingNewWork() {
        AccountInvitationBatchEntity batch = AccountInvitationBatchEntity.builder().id(10L).acceptedCount(1)
                .status(BulkInvitationBatchStatus.PROCESSING).build();
        AccountInvitationItemEntity item = AccountInvitationItemEntity.builder().id(11L).batch(batch)
                .status(BulkInvitationItemStatus.SENDING).build();
        EmailOutboxEntity stale = EmailOutboxEntity.builder().id(12L).status(EmailOutboxStatus.PROCESSING)
                .attemptCount(1).batchItem(item).nextAttemptAt(Instant.now().minusSeconds(60)).build();
        when(outboxRepository.findByStatusAndUpdatedAtBefore(any(), any())).thenReturn(List.of(stale));
        when(outboxRepository.findNextAvailableForUpdate()).thenReturn(Optional.empty());

        assertThat(service.claimNext()).isEmpty();

        assertThat(stale.getStatus()).isEqualTo(EmailOutboxStatus.QUEUED);
        assertThat(stale.getNextAttemptAt()).isAfter(Instant.now().minusSeconds(5));
        assertThat(item.getStatus()).isEqualTo(BulkInvitationItemStatus.QUEUED);
        assertThat(item.getFailureCode()).isEqualTo("SMTP_DELIVERY_TIMEOUT");
    }

    @Test
    void delayedDeliveryCannotReopenAcceptedInvitation() {
        AccountInvitationEntity invitation = AccountInvitationEntity.builder().id(4L)
                .status(InvitationStatus.ACCEPTED).tokenHash("consumed-hash").build();
        EmailOutboxEntity outbox = EmailOutboxEntity.builder().id(5L).invitation(invitation)
                .recipientEmail("activated@example.test").status(EmailOutboxStatus.PROCESSING).build();
        when(outboxRepository.findById(5L)).thenReturn(Optional.of(outbox));
        when(invitationRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(invitation));
        when(userRepository.findByEmail(outbox.getRecipientEmail())).thenReturn(Optional.of(
                UserEntity.builder().isAccountVerified(true).build()));

        assertThatThrownBy(() -> service.prepare(5L, "http://localhost:5173"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(invitation.getTokenHash()).isEqualTo("consumed-hash");
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
    }

    @Test
    void lateDeliveryAcknowledgementPreservesActivatedBatchItem() {
        AccountInvitationItemEntity item = AccountInvitationItemEntity.builder()
                .status(BulkInvitationItemStatus.ACTIVATED).build();
        EmailOutboxEntity outbox = EmailOutboxEntity.builder().id(5L).batchItem(item)
                .status(EmailOutboxStatus.PROCESSING).build();
        when(outboxRepository.findById(5L)).thenReturn(Optional.of(outbox));
        service.markSent(5L);
        assertThat(item.getStatus()).isEqualTo(BulkInvitationItemStatus.ACTIVATED);
    }

    @Test
    void permanentlyFailsStaleMessageAfterFinalClaim() {
        AccountInvitationBatchEntity batch = AccountInvitationBatchEntity.builder().id(10L).acceptedCount(1)
                .status(BulkInvitationBatchStatus.PROCESSING).build();
        AccountInvitationItemEntity item = AccountInvitationItemEntity.builder().id(11L).batch(batch)
                .status(BulkInvitationItemStatus.SENDING).build();
        EmailOutboxEntity stale = EmailOutboxEntity.builder().id(12L).status(EmailOutboxStatus.PROCESSING)
                .attemptCount(3).batchItem(item).build();
        when(outboxRepository.findByStatusAndUpdatedAtBefore(any(), any())).thenReturn(List.of(stale));
        when(itemRepository.countByBatch_IdAndStatus(10L, BulkInvitationItemStatus.SENT)).thenReturn(0L);
        when(itemRepository.countByBatch_IdAndStatus(10L, BulkInvitationItemStatus.EMAIL_FAILED)).thenReturn(1L);
        when(itemRepository.countByBatch_IdAndStatus(10L, BulkInvitationItemStatus.ACTIVATED)).thenReturn(0L);
        when(outboxRepository.findNextAvailableForUpdate()).thenReturn(Optional.empty());

        service.claimNext();

        assertThat(stale.getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
        assertThat(item.getStatus()).isEqualTo(BulkInvitationItemStatus.EMAIL_FAILED);
        assertThat(batch.getStatus()).isEqualTo(BulkInvitationBatchStatus.COMPLETED_WITH_FAILURES);
    }
}
