package com.smartlab.entity;

import com.smartlab.enums.BulkInvitationItemStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "account_invitation_items", uniqueConstraints = @UniqueConstraint(columnNames = {"batch_id", "source_row"}))
public class AccountInvitationItemEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String itemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private AccountInvitationBatchEntity batch;

    @Column(nullable = false)
    private Integer sourceRow;

    @Column(length = 150)
    private String fullName;

    @Column(nullable = false, length = 190)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitation_id")
    private AccountInvitationEntity invitation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BulkInvitationItemStatus status;

    @Column(length = 80)
    private String failureCode;

    @Column(length = 500)
    private String failureMessage;

    @Column(nullable = false)
    private Integer resendCount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
