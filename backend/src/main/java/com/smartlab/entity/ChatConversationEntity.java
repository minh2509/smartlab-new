package com.smartlab.entity;

import com.smartlab.enums.ChatConversationType;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "chat_conversations")
public class ChatConversationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, unique = true, length = 36)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_type", nullable = false, length = 20)
    private ChatConversationType conversationType;

    @Column(length = 200)
    private String title;

    @Column(name = "direct_key", unique = true, length = 100)
    private String directKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private ProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private UserEntity createdBy;

    @Column(name = "last_message_seq", nullable = false)
    private Long lastMessageSeq;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ChatConversationEntity createDirect(UserEntity firstUser, UserEntity secondUser, UserEntity createdBy) {
        return ChatConversationEntity.builder()
                .conversationId(UUID.randomUUID().toString())
                .conversationType(ChatConversationType.DIRECT)
                .directKey(buildDirectKey(firstUser.getId(), secondUser.getId()))
                .createdBy(createdBy)
                .lastMessageSeq(0L)
                .build();
    }

    public static ChatConversationEntity createGroup(String title, UserEntity createdBy, ProjectEntity project) {
        return ChatConversationEntity.builder()
                .conversationId(UUID.randomUUID().toString())
                .conversationType(ChatConversationType.GROUP)
                .title(title)
                .project(project)
                .createdBy(createdBy)
                .lastMessageSeq(0L)
                .build();
    }

    public long nextMessageSequence() {
        long next = (lastMessageSeq == null ? 0L : lastMessageSeq) + 1L;
        this.lastMessageSeq = next;
        return next;
    }

    public void rename(String title) {
        this.title = title;
    }

    public static String buildDirectKey(Long firstUserId, Long secondUserId) {
        if (firstUserId == null || secondUserId == null) {
            throw new IllegalArgumentException("Direct chat users must already be persisted");
        }
        long low = Math.min(firstUserId, secondUserId);
        long high = Math.max(firstUserId, secondUserId);
        if (low == high) {
            throw new IllegalArgumentException("Direct chat requires two different users");
        }
        return low + ":" + high;
    }
}
