package com.smartlab.repo;

import com.smartlab.entity.ChatConversationMemberEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatConversationMemberRepository extends JpaRepository<ChatConversationMemberEntity, Long> {
    @Query("""
            select m
            from ChatConversationMemberEntity m
            join fetch m.user
            where m.conversation.conversationId = :conversationId
              and m.leftAt is null
            order by m.joinedAt, m.id
            """)
    List<ChatConversationMemberEntity> findActiveMembers(@Param("conversationId") String conversationId);

    @Query("""
            select m
            from ChatConversationMemberEntity m
            join fetch m.conversation c
            where m.user.id = :userId
              and m.leftAt is null
            order by m.pinned desc, c.updatedAt desc, c.id desc
            """)
    List<ChatConversationMemberEntity> findActiveByUserId(@Param("userId") Long userId);

    @Query("""
            select m
            from ChatConversationMemberEntity m
            join fetch m.conversation
            where m.conversation.conversationId = :conversationId
              and m.user.id = :userId
            """)
    Optional<ChatConversationMemberEntity> findMembership(
            @Param("conversationId") String conversationId,
            @Param("userId") Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m
            from ChatConversationMemberEntity m
            join fetch m.conversation
            where m.conversation.conversationId = :conversationId
              and m.user.id = :userId
            """)
    Optional<ChatConversationMemberEntity> findMembershipForUpdate(
            @Param("conversationId") String conversationId,
            @Param("userId") Long userId
    );
}
