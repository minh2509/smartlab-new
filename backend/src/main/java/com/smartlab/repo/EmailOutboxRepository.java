package com.smartlab.repo;

import com.smartlab.entity.EmailOutboxEntity;
import com.smartlab.enums.EmailOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxEntity, Long> {
    List<EmailOutboxEntity> findByStatusAndUpdatedAtBefore(EmailOutboxStatus status, Instant updatedAt);

    boolean existsByInvitation_IdAndStatusIn(Long invitationId, List<EmailOutboxStatus> statuses);

    @Query(value = """
            SELECT * FROM email_outbox
            WHERE status = 'QUEUED' AND next_attempt_at <= now()
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<EmailOutboxEntity> findNextAvailableForUpdate();
}
