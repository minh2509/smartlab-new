package com.smartlab.repo;

import com.smartlab.entity.ChatMessageFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageFileRepository extends JpaRepository<ChatMessageFileEntity, Long> {
    List<ChatMessageFileEntity> findByMessageIdOrderByPositionAscIdAsc(Long messageId);
}
