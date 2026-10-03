package com.smartlab.entity;

import com.smartlab.enums.ChatMessageType;
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
        name = "chat_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_messages_conversation_seq",
                columnNames = {"conversation_id", "message_seq"}
        )
)
public class ChatMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversationEntity conversation;

    @Column(name = "message_seq", nullable = false)
    private Long messageSeq;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id")
    private UserEntity sender;

    @Column(name = "client_message_id", length = 64)
    private String clientMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private ChatMessageType messageType;

    @Column(columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_id")
    private ChatMessageEntity replyTo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static ChatMessageEntity create(
            ChatConversationEntity conversation,
            long messageSeq,
            UserEntity sender,
            String clientMessageId,
            ChatMessageType messageType,
            String content,
            ChatMessageEntity replyTo
    ) {
        return ChatMessageEntity.builder()
                .conversation(conversation)
                .messageSeq(messageSeq)
                .sender(sender)
                .clientMessageId(clientMessageId)
                .messageType(messageType)
                .content(content)
                .replyTo(replyTo)
                .build();
    }

    public void edit(String content) {
        this.content = content;
        this.editedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.content = null;
    }
}
