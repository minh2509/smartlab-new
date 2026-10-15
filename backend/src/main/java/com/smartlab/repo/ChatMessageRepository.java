package com.smartlab.repo;

import com.smartlab.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    @Query("""
            select m
            from ChatMessageEntity m
            left join fetch m.sender
            where m.conversation.id = :conversationId
              and m.messageSeq < :beforeSeq
            order by m.messageSeq desc
            """)
    List<ChatMessageEntity> findHistoryBefore(
            @Param("conversationId") Long conversationId,
            @Param("beforeSeq") long beforeSeq,
            Pageable pageable
    );

    @Query("""
            select m
            from ChatMessageEntity m
            left join fetch m.sender
            where m.conversation.id = :conversationId
              and m.messageSeq > :afterSeq
            order by m.messageSeq asc
            """)
    List<ChatMessageEntity> findCatchUpAfter(
            @Param("conversationId") Long conversationId,
            @Param("afterSeq") long afterSeq,
            Pageable pageable
    );

    @Query("""
            select m
            from ChatMessageEntity m
            left join fetch m.sender
            where m.conversation.id = :conversationId
            order by m.messageSeq desc
            """)
    List<ChatMessageEntity> findLatest(
            @Param("conversationId") Long conversationId,
            Pageable pageable
    );

    @Query("""
            select m
            from ChatMessageEntity m
            left join fetch m.sender
            left join fetch m.replyTo reply
            left join fetch reply.sender
            where m.conversation.id in :conversationIds
              and m.messageSeq = m.conversation.lastMessageSeq
            """)
    List<ChatMessageEntity> findLatestByConversationIds(
            @Param("conversationIds") List<Long> conversationIds);

    boolean existsByConversation_IdAndMessageSeqLessThan(Long conversationId, long messageSeq);

    boolean existsByConversation_IdAndMessageSeqGreaterThan(Long conversationId, long messageSeq);

    Optional<ChatMessageEntity> findBySender_IdAndClientMessageId(Long senderId, String clientMessageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ChatMessageEntity m left join fetch m.sender where m.id = :messageId")
    Optional<ChatMessageEntity> findByIdForUpdate(@Param("messageId") Long messageId);

    @Query("""
            select m from ChatMessageEntity m
            left join fetch m.sender
            where m.conversation.id = :conversationId
              and m.deletedAt is null
              and lower(m.content) like lower(concat('%', :query, '%'))
            order by m.messageSeq desc
            """)
    List<ChatMessageEntity> searchContent(
            @Param("conversationId") Long conversationId,
            @Param("query") String query,
            Pageable pageable
    );
}
