package com.smartlab.repo;

import com.smartlab.entity.AccountInvitationBatchRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountInvitationBatchRoleRepository extends JpaRepository<AccountInvitationBatchRoleEntity, Long> {
    List<AccountInvitationBatchRoleEntity> findByBatchBatchIdOrderByRoleCodeAsc(String batchId);
}
