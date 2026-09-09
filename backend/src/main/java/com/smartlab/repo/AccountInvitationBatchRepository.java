package com.smartlab.repo;

import com.smartlab.entity.AccountInvitationBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountInvitationBatchRepository extends JpaRepository<AccountInvitationBatchEntity, Long> {
    Optional<AccountInvitationBatchEntity> findByBatchId(String batchId);
}
