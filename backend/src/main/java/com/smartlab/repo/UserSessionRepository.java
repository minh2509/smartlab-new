package com.smartlab.repo;

import com.smartlab.entity.UserSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Query("""
            select us
            from UserSessionEntity us
            where us.user.id = :userId
              and us.revokedAt is null
              and us.expiresAt > :now
            order by us.createdAt asc, us.id asc
            """)
    List<UserSessionEntity> findActiveByUserIdOrderByOldest(
            @Param("userId") Long userId,
            @Param("now") Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select us
            from UserSessionEntity us
            join fetch us.user
            where us.refreshTokenHash = :refreshTokenHash
            """)
    Optional<UserSessionEntity> findByRefreshTokenHashForUpdate(
            @Param("refreshTokenHash") String refreshTokenHash
    );

    Optional<UserSessionEntity> findByRefreshTokenHash(String refreshTokenHash);


    @Modifying
    @Query("update UserSessionEntity us set us.revokedAt = :revokedAt where us.user.id = :userId and us.revokedAt is null")
    void revokeAllActiveByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);
}
