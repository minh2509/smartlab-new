package com.smartlab.repo;

import com.smartlab.entity.ChatMessageReactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageReactionRepository extends JpaRepository<ChatMessageReactionEntity, Long> {
    @Query("select r from ChatMessageReactionEntity r join fetch r.user where r.message.id in :messageIds order by r.id")
    List<ChatMessageReactionEntity> findByMessageIds(@Param("messageIds") List<Long> messageIds);

    @Query("select r from ChatMessageReactionEntity r where r.message.id = :messageId and r.user.id = :userId and r.emoji = :emoji")
    Optional<ChatMessageReactionEntity> findOne(
            @Param("messageId") Long messageId,
            @Param("userId") Long userId,
            @Param("emoji") String emoji
    );
}
