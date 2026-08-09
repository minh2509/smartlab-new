package com.smartlab.repo;

import com.smartlab.entity.RolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Set;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, Long> {
    @Query("""
            select distinct rp.permission.code
            from RolePermissionEntity rp
            where rp.role.id in :roleIds
              and rp.role.isActive = true
              and rp.permission.isActive = true
            """)
    Set<String> findActivePermissionCodesByRoleIds(@Param("roleIds") Collection<Long> roleIds);

    @Modifying
    void deleteByRoleId(Long roleId);
}
