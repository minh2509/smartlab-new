package com.smartlab.repo;

import com.smartlab.entity.MemberProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberProfileRepository extends JpaRepository<MemberProfileEntity, Long> {
    Optional<MemberProfileEntity> findByUserId(Long userId);

    List<MemberProfileEntity> findByActiveStatusOrderByFeaturedOrderAscIdAsc(String activeStatus);

    @Query("select (count(profile) > 0) from MemberProfileEntity profile where profile.avatarFile.id = :fileId")
    boolean existsByAvatarFileId(@Param("fileId") Long fileId);
}
