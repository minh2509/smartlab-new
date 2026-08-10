package com.smartlab.repo;

import com.smartlab.entity.ProjectMemberEntity;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, Long> {
    Optional<ProjectMemberEntity> findByProject_IdAndUser_Id(Long projectId, Long userId);

    Optional<ProjectMemberEntity> findByProject_IdAndUser_IdAndStatus(
            Long projectId,
            Long userId,
            ProjectMemberStatus status
    );

    boolean existsByProject_IdAndUser_IdAndStatus(
            Long projectId,
            Long userId,
            ProjectMemberStatus status
    );

    boolean existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
            Long projectId,
            Long userId,
            ProjectRole projectRole,
            ProjectMemberStatus status
    );

    List<ProjectMemberEntity> findAllByProject_IdAndProjectRoleAndStatus(
            Long projectId,
            ProjectRole projectRole,
            ProjectMemberStatus status
    );

    @Query("""
            select pm
            from ProjectMemberEntity pm
            join fetch pm.user u
            where pm.project.id in :projectIds
              and pm.projectRole = com.smartlab.enums.ProjectRole.LEADER
              and pm.status = com.smartlab.enums.ProjectMemberStatus.ACTIVE
              and pm.project.deletedAt is null
            order by pm.project.id, u.id
            """)
    List<ProjectMemberEntity> findActiveLeadersByProjectIds(
            @Param("projectIds") Collection<Long> projectIds
    );

    @Query("""
            select pm.project.id
            from ProjectMemberEntity pm
            where pm.user.id = :userId
              and pm.status = com.smartlab.enums.ProjectMemberStatus.ACTIVE
              and pm.project.deletedAt is null
            order by pm.project.id
            """)
    List<Long> findActiveProjectIdsByUserId(@Param("userId") Long userId);
}
