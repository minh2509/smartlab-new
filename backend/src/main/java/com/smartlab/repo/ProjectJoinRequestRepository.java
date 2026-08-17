package com.smartlab.repo;

import com.smartlab.entity.ProjectJoinRequestEntity;
import com.smartlab.enums.ProjectJoinRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectJoinRequestRepository extends JpaRepository<ProjectJoinRequestEntity, Long> {
    @Query("""
            select request
            from ProjectJoinRequestEntity request
            join fetch request.project project
            join fetch request.requester requester
            left join fetch request.reviewedBy reviewer
            where project.id = :projectId
              and requester.id = :requesterId
            order by request.createdAt desc, request.id desc
            """)
    List<ProjectJoinRequestEntity> findLatestForRequester(
            @Param("projectId") Long projectId,
            @Param("requesterId") Long requesterId,
            org.springframework.data.domain.Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request
            from ProjectJoinRequestEntity request
            where request.project.id = :projectId
              and request.requester.id = :requesterId
              and request.status = com.smartlab.enums.ProjectJoinRequestStatus.PENDING
            """)
    Optional<ProjectJoinRequestEntity> findPendingForRequesterForUpdate(
            @Param("projectId") Long projectId,
            @Param("requesterId") Long requesterId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request
            from ProjectJoinRequestEntity request
            join fetch request.project
            join fetch request.requester
            left join fetch request.reviewedBy
            where request.id = :requestId
              and request.project.id = :projectId
            """)
    Optional<ProjectJoinRequestEntity> findByProjectAndIdForUpdate(
            @Param("projectId") Long projectId,
            @Param("requestId") Long requestId
    );

    @Query("""
            select request
            from ProjectJoinRequestEntity request
            join fetch request.project project
            join fetch request.requester requester
            left join fetch request.reviewedBy reviewer
            where project.id = :projectId
              and request.status = :status
            order by request.createdAt desc, request.id desc
            """)
    List<ProjectJoinRequestEntity> findForManagement(
            @Param("projectId") Long projectId,
            @Param("status") ProjectJoinRequestStatus status
    );
}
