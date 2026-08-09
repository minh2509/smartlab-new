package com.smartlab.repo;

import com.smartlab.entity.UserPermissionOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverrideEntity, Long> {
    List<UserPermissionOverrideEntity> findByUserId(Long userId);

    Optional<UserPermissionOverrideEntity> findByUserIdAndPermissionId(Long userId, Long permissionId);

    @Modifying
    void deleteByUserIdAndPermissionId(Long userId, Long permissionId);
}
