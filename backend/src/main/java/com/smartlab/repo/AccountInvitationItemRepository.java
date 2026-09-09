package com.smartlab.repo;

import com.smartlab.entity.AccountInvitationItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountInvitationItemRepository extends JpaRepository<AccountInvitationItemEntity, Long> {
    List<AccountInvitationItemEntity> findByBatchBatchIdOrderBySourceRowAsc(String batchId);
    Optional<AccountInvitationItemEntity> findByItemIdAndBatchBatchId(String itemId, String batchId);
    Optional<AccountInvitationItemEntity> findByInvitationId(Long invitationId);
    long countByBatch_IdAndStatus(Long batchId, com.smartlab.enums.BulkInvitationItemStatus status);
}
