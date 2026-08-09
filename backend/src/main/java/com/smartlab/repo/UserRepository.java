package com.smartlab.repo;

import com.smartlab.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity,Long> {
    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByUserId(String userId);

    Boolean existsByEmail(String email);

}
