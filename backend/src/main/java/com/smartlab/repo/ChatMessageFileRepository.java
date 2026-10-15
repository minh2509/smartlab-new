package com.smartlab.repo;

import com.smartlab.entity.ChatMessageFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;

@Repository
public interface ChatMessageFileRepository extends JpaRepository<ChatMessageFileEntity, Long> {
    List<ChatMessageFileEntity> findByMessageIdOrderByPositionAscIdAsc(Long messageId);

    boolean existsByFile_Id(Long fileId);

    @Query("""
            select count(cm.id)
            from ChatMessageFileEntity mf, ChatConversationMemberEntity cm
            where mf.file.id = :fileId
              and mf.message.deletedAt is null
              and cm.conversation.id = mf.message.conversation.id
              and lower(cm.user.email) = lower(:email)
              and cm.leftAt is null
            """)
    long countActiveMembershipsForFile(
            @Param("fileId") Long fileId,
            @Param("email") String email
    );

    @Query("""
            select mf
            from ChatMessageFileEntity mf
            join fetch mf.file
            where mf.message.id in :messageIds
            order by mf.message.id, mf.position, mf.id
            """)
    List<ChatMessageFileEntity> findByMessageIds(@Param("messageIds") Collection<Long> messageIds);
}
