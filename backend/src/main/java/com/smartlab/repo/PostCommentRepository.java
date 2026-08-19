package com.smartlab.repo;

import com.smartlab.entity.PostCommentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostCommentRepository extends JpaRepository<PostCommentEntity, Long> {
    interface CommentCountView {
        Long getPostId();
        long getCount();
    }

    @Query("""
            select c from PostCommentEntity c
            where c.postId = :postId
              and c.deletedAt is null
              and (
                c.createdAt < :cursorCreatedAt
                or (c.createdAt = :cursorCreatedAt and c.id < :cursorId)
              )
            order by c.createdAt desc, c.id desc
            """)
    List<PostCommentEntity> findActiveCreatedDescendingPage(
            @Param("postId") Long postId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
            select c from PostCommentEntity c where c.postId = :postId and c.authorUserId = :authorUserId and c.deletedAt is null
              and (c.createdAt < :cursorTimestamp or (c.createdAt = :cursorTimestamp and c.id < :cursorId))
            order by c.createdAt desc, c.id desc
            """)
    List<PostCommentEntity> findMineActiveCreatedDescendingPage(@Param("postId") Long postId, @Param("authorUserId") Long authorUserId, @Param("cursorTimestamp") Instant cursorTimestamp, @Param("cursorId") Long cursorId, Pageable pageable);

    @Query("""
            select c from PostCommentEntity c where c.postId = :postId and c.deletedAt is null
              and (c.createdAt > :cursorTimestamp or (c.createdAt = :cursorTimestamp and c.id > :cursorId))
            order by c.createdAt asc, c.id asc
            """)
    List<PostCommentEntity> findActiveCreatedAscendingPage(@Param("postId") Long postId, @Param("cursorTimestamp") Instant cursorTimestamp, @Param("cursorId") Long cursorId, Pageable pageable);

    @Query("""
            select c from PostCommentEntity c where c.postId = :postId and c.authorUserId = :authorUserId and c.deletedAt is null
              and (c.createdAt > :cursorTimestamp or (c.createdAt = :cursorTimestamp and c.id > :cursorId))
            order by c.createdAt asc, c.id asc
            """)
    List<PostCommentEntity> findMineActiveCreatedAscendingPage(@Param("postId") Long postId, @Param("authorUserId") Long authorUserId, @Param("cursorTimestamp") Instant cursorTimestamp, @Param("cursorId") Long cursorId, Pageable pageable);

    @Query("""
            select c from PostCommentEntity c where c.postId = :postId and c.deletedAt is null
              and (c.updatedAt < :cursorTimestamp or (c.updatedAt = :cursorTimestamp and c.id < :cursorId))
            order by c.updatedAt desc, c.id desc
            """)
    List<PostCommentEntity> findActiveUpdatedDescendingPage(@Param("postId") Long postId, @Param("cursorTimestamp") Instant cursorTimestamp, @Param("cursorId") Long cursorId, Pageable pageable);

    @Query("""
            select c from PostCommentEntity c where c.postId = :postId and c.authorUserId = :authorUserId and c.deletedAt is null
              and (c.updatedAt < :cursorTimestamp or (c.updatedAt = :cursorTimestamp and c.id < :cursorId))
            order by c.updatedAt desc, c.id desc
            """)
    List<PostCommentEntity> findMineActiveUpdatedDescendingPage(@Param("postId") Long postId, @Param("authorUserId") Long authorUserId, @Param("cursorTimestamp") Instant cursorTimestamp, @Param("cursorId") Long cursorId, Pageable pageable);

    @Query("""
            select c from PostCommentEntity c where c.postId = :postId and c.deletedAt is null
              and (c.updatedAt > :cursorTimestamp or (c.updatedAt = :cursorTimestamp and c.id > :cursorId))
            order by c.updatedAt asc, c.id asc
            """)
    List<PostCommentEntity> findActiveUpdatedAscendingPage(@Param("postId") Long postId, @Param("cursorTimestamp") Instant cursorTimestamp, @Param("cursorId") Long cursorId, Pageable pageable);

    @Query("""
            select c from PostCommentEntity c where c.postId = :postId and c.authorUserId = :authorUserId and c.deletedAt is null
              and (c.updatedAt > :cursorTimestamp or (c.updatedAt = :cursorTimestamp and c.id > :cursorId))
            order by c.updatedAt asc, c.id asc
            """)
    List<PostCommentEntity> findMineActiveUpdatedAscendingPage(@Param("postId") Long postId, @Param("authorUserId") Long authorUserId, @Param("cursorTimestamp") Instant cursorTimestamp, @Param("cursorId") Long cursorId, Pageable pageable);

    @Query("""
            select c from PostCommentEntity c
            where c.id = :commentId
              and c.postId = :postId
              and c.deletedAt is null
            """)
    Optional<PostCommentEntity> findActiveByIdAndPostId(
            @Param("commentId") Long commentId,
            @Param("postId") Long postId
    );

    @Query("""
            select c.postId as postId, count(c.id) as count
            from PostCommentEntity c
            where c.postId in :postIds and c.deletedAt is null
            group by c.postId
            """)
    List<CommentCountView> countActiveByPostIds(@Param("postIds") Collection<Long> postIds);
}
