package com.smartlab.entity;

import com.smartlab.enums.BulkInvitationBatchStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "account_invitation_batches")
public class AccountInvitationBatchEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String batchId;

    @Column(nullable = false, length = 190)
    private String createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BulkInvitationBatchStatus status;

    @Column(nullable = false)
    private Integer requestedCount;

    @Column(nullable = false)
    private Integer acceptedCount;

    @Column(nullable = false)
    private Integer rejectedCount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant completedAt;
}
