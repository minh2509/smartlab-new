package com.smartlab.service;

import com.smartlab.dto.response.ChatEventEnvelope;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;

@Component
public class ChatRealtimePublisher {
    private static final String USER_QUEUE = "/queue/chat";

    private final SimpMessagingTemplate messagingTemplate;

    public ChatRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishAfterCommit(
            String event,
            String conversationId,
            Object data,
            Collection<String> recipients
    ) {
        ChatEventEnvelope envelope = new ChatEventEnvelope(
                event,
                UUID.randomUUID(),
                Instant.now(),
                conversationId,
                data
        );
        LinkedHashSet<String> users = new LinkedHashSet<>(recipients);
        Runnable publish = () -> users.forEach(user ->
                messagingTemplate.convertAndSendToUser(user, USER_QUEUE, envelope));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}
