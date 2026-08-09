package com.smartlab.repo;

import com.smartlab.entity.RoleEntity;
import com.smartlab.entity.UserRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRoleEntity, Long> {
    @Query("select ur.role from UserRoleEntity ur where ur.user.id = :userId")
    List<RoleEntity> findRolesByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndRoleIsActiveFalse(Long userId);

    @Modifying
    void deleteByUserId(Long userId);
}
