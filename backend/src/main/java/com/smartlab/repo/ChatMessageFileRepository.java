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

    @Query("""
            select mf
            from ChatMessageFileEntity mf
            join fetch mf.file
            where mf.message.id in :messageIds
            order by mf.message.id, mf.position, mf.id
            """)
    List<ChatMessageFileEntity> findByMessageIds(@Param("messageIds") Collection<Long> messageIds);
}
