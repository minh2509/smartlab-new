package com.smartlab.repo;

import com.smartlab.entity.UserSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSessionEntity, Long> {
    Optional<UserSessionEntity> findBySessionIdAndRevokedAtIsNull(String sessionId);

    List<UserSessionEntity> findByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(Long userId);

    @Modifying
    @Query("update UserSessionEntity us set us.revokedAt = :revokedAt where us.user.id = :userId and us.revokedAt is null")
    void revokeAllActiveByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);
}
