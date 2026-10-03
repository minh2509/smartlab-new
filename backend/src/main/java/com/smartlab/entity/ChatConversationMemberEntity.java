package com.smartlab.entity;

import com.smartlab.enums.ChatMembershipRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
        name = "chat_conversation_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_conversation_members_conversation_user",
                columnNames = {"conversation_id", "user_id"}
        )
)
public class ChatConversationMemberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversationEntity conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_role", nullable = false, length = 20)
    private ChatMembershipRole membershipRole;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "last_read_seq", nullable = false)
    private Long lastReadSeq;

    @Column(name = "is_muted", nullable = false)
    private Boolean muted;

    @Column(name = "is_pinned", nullable = false)
    private Boolean pinned;

    @Column(name = "archived_at")
    private Instant archivedAt;

    public static ChatConversationMemberEntity create(
            ChatConversationEntity conversation,
            UserEntity user,
            ChatMembershipRole role
    ) {
        return ChatConversationMemberEntity.builder()
                .conversation(conversation)
                .user(user)
                .membershipRole(role)
                .lastReadSeq(0L)
                .muted(false)
                .pinned(false)
                .build();
    }

    public boolean isActive() {
        return leftAt == null;
    }

    public void markRead(long sequence) {
        long current = lastReadSeq == null ? 0L : lastReadSeq;
        if (sequence > current) {
            lastReadSeq = sequence;
        }
    }

    public void leave() {
        if (leftAt == null) {
            leftAt = Instant.now();
        }
    }

    public void rejoin(ChatMembershipRole role) {
        this.membershipRole = role;
        this.leftAt = null;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public void archive() {
        this.archivedAt = Instant.now();
    }

    public void unarchive() {
        this.archivedAt = null;
    }
}
