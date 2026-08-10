package com.smartlab.repo;

import com.smartlab.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    java.util.List<NotificationEntity> findByRecipientUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long recipientUserId);

    java.util.Optional<NotificationEntity> findByIdAndRecipientUserIdAndDeletedAtIsNull(Long id, Long recipientUserId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update NotificationEntity n set n.isRead = true where n.recipientUserId = :recipientUserId and n.deletedAt is null")
    int markAllReadByRecipientUserId(@Param("recipientUserId") Long recipientUserId);
}
