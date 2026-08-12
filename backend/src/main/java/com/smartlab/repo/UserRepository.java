package com.smartlab.repo;

import com.smartlab.entity.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity,Long> {
    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByUserId(String userId);

    @Query("""
            select u
            from UserEntity u
            order by lower(u.name), lower(u.email), u.id
            """)
    Page<UserEntity> findAccounts(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.userId in :userIds order by u.id")
    List<UserEntity> findAllByUserIdInForUpdate(@Param("userIds") Collection<String> userIds);

    @Query("""
            select u
            from UserEntity u
            where u.isActive = true
              and not exists (
                  select ur.id
                  from UserRoleEntity ur
                  where ur.user = u
                    and ur.role.isActive = false
              )
              and (
                  :query = ''
                  or lower(u.name) like concat('%', lower(:query), '%')
                  or lower(u.email) like concat('%', lower(:query), '%')
              )
            order by lower(u.name), lower(u.email), u.id
            """)
    List<UserEntity> findAssignableLeaderCandidates(
            @Param("query") String query,
            Pageable pageable
    );

    Boolean existsByEmail(String email);

}
