package com.smartlab.service.impl;

import com.smartlab.entity.*;
import com.smartlab.enums.BulkInvitationBatchStatus;
import com.smartlab.enums.BulkInvitationItemStatus;
import com.smartlab.enums.EmailOutboxStatus;
import com.smartlab.enums.InvitationStatus;
import com.smartlab.repo.AccountInvitationItemRepository;
import com.smartlab.repo.EmailOutboxRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.TokenHashService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

/** Transactional state transitions for the account-invitation outbox. */
@Service
@RequiredArgsConstructor
public class AccountInvitationOutboxStateService {
    private final EmailOutboxRepository emailOutboxRepository;
    private final AccountInvitationItemRepository itemRepository;
    private final UserRepository userRepository;
    private final TokenHashService tokenHashService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${smartlab.invite.ttl-hours:72}")
    private long invitationTtlHours;

    @Value("${smartlab.email-outbox.max-attempts:3}")
    private int maxAttempts;

    @Value("${smartlab.email-outbox.processing-timeout-seconds:900}")
    private long processingTimeoutSeconds;

    @Transactional
    public Optional<Long> claimNext() {
        recoverStaleClaims();
        return emailOutboxRepository.findNextAvailableForUpdate().map(outbox -> {
            outbox.setStatus(EmailOutboxStatus.PROCESSING);
            outbox.setAttemptCount(outbox.getAttemptCount() + 1);
            if (outbox.getBatchItem() != null) {
                outbox.getBatchItem().setStatus(BulkInvitationItemStatus.SENDING);
                outbox.getBatchItem().getBatch().setStatus(BulkInvitationBatchStatus.PROCESSING);
            }
            return outbox.getId();
        });
    }

    /** Recovers work claimed by a process that stopped before SMTP delivery completed. */
    private void recoverStaleClaims() {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(processingTimeoutSeconds);
        for (EmailOutboxEntity outbox : emailOutboxRepository.findByStatusAndUpdatedAtBefore(EmailOutboxStatus.PROCESSING, cutoff)) {
            boolean terminal = outbox.getAttemptCount() >= maxAttempts;
            outbox.setStatus(terminal ? EmailOutboxStatus.FAILED : EmailOutboxStatus.QUEUED);
            outbox.setLastError("Delivery worker claim timed out" + (terminal ? " after final attempt" : "; retry scheduled"));
            if (!terminal) outbox.setNextAttemptAt(now);
            if (outbox.getBatchItem() != null) {
                AccountInvitationItemEntity item = outbox.getBatchItem();
                item.setFailureCode("SMTP_DELIVERY_TIMEOUT");
                item.setFailureMessage(outbox.getLastError());
                item.setStatus(terminal ? BulkInvitationItemStatus.EMAIL_FAILED : BulkInvitationItemStatus.QUEUED);
                refreshBatch(item.getBatch());
            }
        }
    }

    /** Produces a raw token only after the delivery work has been durably claimed. */
    @Transactional
    public PreparedInvitation prepare(Long outboxId, String frontendBaseUrl) {
        EmailOutboxEntity outbox = requireProcessing(outboxId);
        AccountInvitationEntity invitation = outbox.getInvitation();
        String rawToken = randomToken(48);
        invitation.setTokenHash(tokenHashService.sha256(rawToken));
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(Instant.now().plus(invitationTtlHours, ChronoUnit.HOURS));
        String fullName = outbox.getBatchItem() == null
                ? userRepository.findByEmail(outbox.getRecipientEmail()).map(UserEntity::getName).orElse("")
                : outbox.getBatchItem().getFullName();
        String link = frontendBaseUrl.replaceAll("/+$", "") + "/accept-invite?token=" + rawToken;
        return new PreparedInvitation(outbox.getRecipientEmail(), fullName, link, invitation.getExpiresAt());
    }

    @Transactional
    public void markSent(Long outboxId) {
        EmailOutboxEntity outbox = requireProcessing(outboxId);
        outbox.setStatus(EmailOutboxStatus.SENT);
        outbox.setSentAt(Instant.now());
        outbox.setLastError(null);
        if (outbox.getBatchItem() != null) {
            AccountInvitationItemEntity item = outbox.getBatchItem();
            item.setStatus(BulkInvitationItemStatus.SENT);
            item.setFailureCode(null);
            item.setFailureMessage(null);
            refreshBatch(item.getBatch());
        }
    }

    @Transactional
    public void markFailed(Long outboxId, Exception exception) {
        EmailOutboxEntity outbox = requireProcessing(outboxId);
        String reason = safeMessage(exception);
        boolean terminal = outbox.getAttemptCount() >= maxAttempts;
        outbox.setLastError(reason);
        outbox.setStatus(terminal ? EmailOutboxStatus.FAILED : EmailOutboxStatus.QUEUED);
        if (!terminal) outbox.setNextAttemptAt(Instant.now().plusSeconds(30L * outbox.getAttemptCount()));
        if (outbox.getBatchItem() != null) {
            AccountInvitationItemEntity item = outbox.getBatchItem();
            item.setFailureCode("SMTP_DELIVERY_FAILED");
            item.setFailureMessage(reason);
            item.setStatus(terminal ? BulkInvitationItemStatus.EMAIL_FAILED : BulkInvitationItemStatus.QUEUED);
            refreshBatch(item.getBatch());
        }
    }

    private EmailOutboxEntity requireProcessing(Long outboxId) {
        EmailOutboxEntity outbox = emailOutboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalStateException("Outbox message was not found"));
        if (outbox.getStatus() != EmailOutboxStatus.PROCESSING) {
            throw new IllegalStateException("Outbox message is not claimed for delivery");
        }
        return outbox;
    }

    private void refreshBatch(AccountInvitationBatchEntity batch) {
        long terminal = itemRepository.countByBatch_IdAndStatus(batch.getId(), BulkInvitationItemStatus.SENT)
                + itemRepository.countByBatch_IdAndStatus(batch.getId(), BulkInvitationItemStatus.EMAIL_FAILED)
                + itemRepository.countByBatch_IdAndStatus(batch.getId(), BulkInvitationItemStatus.ACTIVATED);
        if (terminal < batch.getAcceptedCount()) {
            batch.setStatus(BulkInvitationBatchStatus.PROCESSING);
            return;
        }
        long failed = itemRepository.countByBatch_IdAndStatus(batch.getId(), BulkInvitationItemStatus.EMAIL_FAILED);
        batch.setStatus(failed == 0 ? BulkInvitationBatchStatus.COMPLETED : BulkInvitationBatchStatus.COMPLETED_WITH_FAILURES);
        batch.setCompletedAt(Instant.now());
    }

    @Transactional
    public void markActivated(Long invitationId) {
        itemRepository.findByInvitationId(invitationId).ifPresent(item -> {
            item.setStatus(BulkInvitationItemStatus.ACTIVATED);
            item.setFailureCode(null);
            item.setFailureMessage(null);
            refreshBatch(item.getBatch());
        });
    }

    private String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String safeMessage(Exception exception) {
        String value = exception.getMessage();
        String safe = value == null || value.isBlank() ? exception.getClass().getSimpleName() : value;
        return safe.substring(0, Math.min(500, safe.length()));
    }

    public record PreparedInvitation(String recipientEmail, String fullName, String invitationLink, Instant expiresAt) { }
}
