package com.smartlab.repo;

import com.smartlab.entity.AccountInvitationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountInvitationRepository extends JpaRepository<AccountInvitationEntity, Long> {
    Optional<AccountInvitationEntity> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountInvitationEntity> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from AccountInvitationEntity i where i.email = :email")
    Optional<AccountInvitationEntity> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from AccountInvitationEntity i where i.id = :id")
    Optional<AccountInvitationEntity> findByIdForUpdate(@Param("id") Long id);
}
