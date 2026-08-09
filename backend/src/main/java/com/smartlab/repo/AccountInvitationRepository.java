package com.smartlab.repo;

import com.smartlab.entity.AccountInvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountInvitationRepository extends JpaRepository<AccountInvitationEntity, Long> {
    Optional<AccountInvitationEntity> findByEmail(String email);

    Optional<AccountInvitationEntity> findByTokenHash(String tokenHash);
}
