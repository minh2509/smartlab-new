package com.smartlab.repo;

import com.smartlab.entity.ChatConversationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversationEntity, Long> {
    Optional<ChatConversationEntity> findByConversationId(String conversationId);

    Optional<ChatConversationEntity> findByDirectKey(String directKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChatConversationEntity c where c.conversationId = :conversationId")
    Optional<ChatConversationEntity> findByConversationIdForUpdate(@Param("conversationId") String conversationId);
}
