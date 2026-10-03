package com.smartlab.repo;

import com.smartlab.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    Optional<ChatMessageEntity> findBySender_IdAndClientMessageId(Long senderId, String clientMessageId);
}
