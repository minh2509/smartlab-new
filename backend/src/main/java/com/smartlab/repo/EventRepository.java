package com.smartlab.repo;

import com.smartlab.entity.EventEntity;
import com.smartlab.enums.EventStatus;
import com.smartlab.enums.EventVisibility;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {
    @Query("""
            select e from EventEntity e
            where e.deletedAt is null and e.visibility = :visibility
              and (:status is null or e.status = :status)
              and (:upcoming is null or (:upcoming = true and e.startAt >= :now) or (:upcoming = false and e.startAt < :now))
            order by case when e.startAt >= :now then 0 else 1 end, e.startAt, e.id
            """)
    List<EventEntity> findPublicEventsLimited(
            @Param("visibility") EventVisibility visibility, @Param("status") EventStatus status,
            @Param("upcoming") Boolean upcoming, @Param("now") Instant now, Pageable pageable);

    @Query("""
            select e from EventEntity e
            where e.deletedAt is null and e.visibility = :visibility
              and (:status is null or e.status = :status)
              and (:upcoming is null or (:upcoming = true and e.startAt >= :now) or (:upcoming = false and e.startAt < :now))
            order by e.startAt desc, e.id desc
            """)
    List<EventEntity> findPublicEventsLatest(
            @Param("visibility") EventVisibility visibility, @Param("status") EventStatus status,
            @Param("upcoming") Boolean upcoming, @Param("now") Instant now, Pageable pageable);

    @Query("""
            select e
            from EventEntity e
            where e.deletedAt is null
              and e.visibility = :visibility
              and (:status is null or e.status = :status)
              and (
                :upcoming is null
                or (:upcoming = true and e.startAt >= :now)
                or (:upcoming = false and e.startAt < :now)
              )
              and (
                :query is null
                or lower(e.title) like lower(concat('%', :query, '%'))
                or lower(coalesce(e.content, '')) like lower(concat('%', :query, '%'))
                or lower(coalesce(e.location, '')) like lower(concat('%', :query, '%'))
              )
            order by e.startAt desc, e.id desc
            """)
    Page<EventEntity> findPublicEventsPage(
            @Param("visibility") EventVisibility visibility,
            @Param("status") EventStatus status,
            @Param("upcoming") Boolean upcoming,
            @Param("query") String query,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("""
            select e
            from EventEntity e
            where e.deletedAt is null
              and (:projectId is null or e.projectId = :projectId)
              and (:status is null or e.status = :status)
              and (
                :upcoming is null
                or (:upcoming = true and e.startAt >= :now)
                or (:upcoming = false and e.startAt < :now)
              )
            order by
              case when e.startAt >= :now then 0 else 1 end,
              e.startAt,
              e.id
            """)
    List<EventEntity> findActiveEvents(
            @Param("projectId") Long projectId,
            @Param("status") EventStatus status,
            @Param("upcoming") Boolean upcoming,
            @Param("now") Instant now
    );

    @Query("""
            select e
            from EventEntity e
            where e.id = :id
              and e.deletedAt is null
            """)
    Optional<EventEntity> findActiveById(@Param("id") Long id);

    @Query("""
            select e
            from EventEntity e
            where e.id = :id
              and e.deletedAt is null
              and e.visibility = :visibility
            """)
    Optional<EventEntity> findPublicById(
            @Param("id") Long id,
            @Param("visibility") EventVisibility visibility
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e
            from EventEntity e
            where e.id = :id
              and e.deletedAt is null
            """)
    Optional<EventEntity> findActiveByIdForUpdate(@Param("id") Long id);
}
