package com.smartlab.repo;

import com.smartlab.entity.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {
    Optional<PermissionEntity> findByCode(String code);

    List<PermissionEntity> findByCodeIn(Collection<String> codes);

    boolean existsByCode(String code);
}
