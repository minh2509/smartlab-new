package com.smartlab.entity;

import com.smartlab.enums.EmailOutboxStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "email_outbox")
public class EmailOutboxEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String messageId;

    @Column(nullable = false, length = 100)
    private String templateCode;

    @Column(nullable = false, length = 190)
    private String recipientEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitation_id")
    private AccountInvitationEntity invitation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_item_id")
    private AccountInvitationItemEntity batchItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailOutboxStatus status;

    @Column(nullable = false)
    private Integer attemptCount;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    @Column(length = 500)
    private String lastError;

    private Instant sentAt;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp @Column(nullable = false)
    private Instant updatedAt;
}
