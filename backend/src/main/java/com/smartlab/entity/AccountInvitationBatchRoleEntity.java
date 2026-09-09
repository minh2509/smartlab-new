package com.smartlab.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "account_invitation_batch_roles", uniqueConstraints = @UniqueConstraint(columnNames = {"batch_id", "role_id"}))
public class AccountInvitationBatchRoleEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private AccountInvitationBatchEntity batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleEntity role;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
