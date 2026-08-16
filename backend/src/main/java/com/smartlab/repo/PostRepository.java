package com.smartlab.repo;

import com.smartlab.entity.PostEntity;
import com.smartlab.enums.PostStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<PostEntity, Long> {

    boolean existsBySlug(String slug);

    @Query("""
            select p from PostEntity p
            where p.deletedAt is null
              and p.status = com.smartlab.enums.PostStatus.PUBLISHED
              and (
                p.visibility in (com.smartlab.enums.PostVisibility.PUBLIC, com.smartlab.enums.PostVisibility.LAB)
                or (
                  p.visibility = com.smartlab.enums.PostVisibility.PROJECT
                  and p.projectId is not null
                  and p.projectId in :activeProjectIds
                )
              )
              and (
                p.publishedAt < :cursorPublishedAt
                or (p.publishedAt = :cursorPublishedAt and p.id < :cursorId)
              )
            order by p.publishedAt desc, p.id desc
            """)
    List<PostEntity> findActivePublishedFeedPage(
            @Param("activeProjectIds") List<Long> activeProjectIds,
            @Param("cursorPublishedAt") Instant cursorPublishedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
            select p from PostEntity p
            where p.deletedAt is null
              and p.status = com.smartlab.enums.PostStatus.PUBLISHED
              and p.visibility = com.smartlab.enums.PostVisibility.PUBLIC
              and (
                p.publishedAt < :cursorPublishedAt
                or (p.publishedAt = :cursorPublishedAt and p.id < :cursorId)
              )
            order by p.publishedAt desc, p.id desc
            """)
    List<PostEntity> findActivePublicPublishedFeedPage(
            @Param("cursorPublishedAt") Instant cursorPublishedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
            select p from PostEntity p
            where p.deletedAt is null and p.authorUserId = :authorUserId
            order by p.updatedAt desc, p.createdAt desc, p.id desc
            """)
    List<PostEntity> findActiveOwnedByAuthorUserId(@Param("authorUserId") Long authorUserId);

    @Query("""
            select p from PostEntity p
            where p.deletedAt is null
              and (
                p.authorUserId = :viewerUserId
                or (
                  p.status = com.smartlab.enums.PostStatus.PUBLISHED
                  and (
                    p.visibility in (com.smartlab.enums.PostVisibility.PUBLIC, com.smartlab.enums.PostVisibility.LAB)
                    or (p.visibility = com.smartlab.enums.PostVisibility.PROJECT
                        and p.projectId is not null and p.projectId in :activeProjectIds)
                  )
                )
              )
            order by p.createdAt desc, p.id desc
            """)
    List<PostEntity> findActiveReadableByViewerUserId(
            @Param("viewerUserId") Long viewerUserId,
            @Param("activeProjectIds") List<Long> activeProjectIds
    );

    @Query("""
            select p from PostEntity p
            where p.deletedAt is null
              and p.status = com.smartlab.enums.PostStatus.PENDING_REVIEW
              and (
                p.authorUserId is null
                or p.authorUserId <> :reviewerUserId
              )
            order by p.createdAt desc, p.id desc
            """)
    List<PostEntity> findActivePendingReviewableByReviewerUserId(
            @Param("reviewerUserId") Long reviewerUserId
    );

    @Query("""
            select p from PostEntity p
            where p.id = :id
              and p.deletedAt is null
              and p.status = com.smartlab.enums.PostStatus.PENDING_REVIEW
              and (
                p.authorUserId is null
                or p.authorUserId <> :reviewerUserId
              )
            """)
    Optional<PostEntity> findActivePendingReviewableByIdAndReviewerUserId(
            @Param("id") Long postId,
            @Param("reviewerUserId") Long reviewerUserId
    );

    @Query("""
            select p from PostEntity p
            where p.slug = :slug
              and p.deletedAt is null
            """)
    Optional<PostEntity> findActiveBySlug(@Param("slug") String slug);

    @Query("""
            select p from PostEntity p
            where p.slug = :slug
              and p.deletedAt is null
              and p.status = com.smartlab.enums.PostStatus.PUBLISHED
              and p.visibility = com.smartlab.enums.PostVisibility.PUBLIC
            """)
    Optional<PostEntity> findActivePublishedPublicBySlug(@Param("slug") String slug);

    @Query("""
            select p from PostEntity p
            where p.id = :id
              and p.deletedAt is null
            """)
    Optional<PostEntity> findActiveById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from PostEntity p
            where p.id = :id
              and p.deletedAt is null
            """)
    Optional<PostEntity> findActiveByIdForUpdate(@Param("id") Long id);

    @Query("""
            select p from PostEntity p
            where p.id = :id
              and p.authorUserId = :authorUserId
              and p.deletedAt is null
              and p.status = :status
            """)
    Optional<PostEntity> findOwnedActiveByIdAndStatus(
            @Param("id") Long id,
            @Param("authorUserId") Long authorUserId,
            @Param("status") PostStatus status
    );

    @Modifying
    @Query("""
            update PostEntity p
            set p.deletedAt = :mutationInstant,
                p.updatedAt = :mutationInstant
            where p.id = :id
              and p.authorUserId = :authorUserId
              and p.deletedAt is null
              and p.status = com.smartlab.enums.PostStatus.DRAFT
            """)
    int softDeleteOwnedDraft(
            @Param("id") Long id,
            @Param("authorUserId") Long authorUserId,
            @Param("mutationInstant") Instant mutationInstant
    );
}
