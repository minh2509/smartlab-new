package com.smartlab.service.impl;

import com.smartlab.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Delivers a bounded number of account invitations each polling cycle. */
@Component
@RequiredArgsConstructor
public class AccountInvitationOutboxProcessor {
    private final AccountInvitationOutboxStateService stateService;
    private final EmailService emailService;

    @Value("${smartlab.frontend.base-url:http://127.0.0.1:5173}")
    private String frontendBaseUrl;

    @Value("${smartlab.email-outbox.max-per-poll:10}")
    private int maxPerPoll;

    @Scheduled(fixedDelayString = "${smartlab.email-outbox.poll-ms:5000}")
    public void processAvailableInvitations() {
        for (int processed = 0; processed < maxPerPoll; processed++) {
            Long outboxId = stateService.claimNext().orElse(null);
            if (outboxId == null) return;
            try {
                AccountInvitationOutboxStateService.PreparedInvitation invitation = stateService.prepare(outboxId, frontendBaseUrl);
                emailService.sendInvitationEmail(invitation.recipientEmail(), invitation.fullName(),
                        invitation.invitationLink(), invitation.expiresAt());
                stateService.markSent(outboxId);
            } catch (Exception exception) {
                stateService.markFailed(outboxId, exception);
            }
        }
    }
}
