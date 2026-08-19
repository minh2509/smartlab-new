package com.smartlab.repo;

import com.smartlab.entity.PostReactionEntity;
import com.smartlab.enums.PostReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PostReactionRepository extends JpaRepository<PostReactionEntity, Long> {
    interface ReactionCountView {
        Long getPostId();
        PostReactionType getReactionType();
        long getCount();
    }

    Optional<PostReactionEntity> findByPostIdAndUserId(Long postId, Long userId);

    List<PostReactionEntity> findAllByPostIdInAndUserId(Collection<Long> postIds, Long userId);

    @Query("""
            select r from PostReactionEntity r where r.postId = :postId
              and (r.updatedAt < :cursorUpdatedAt or (r.updatedAt = :cursorUpdatedAt and r.id < :cursorId))
            order by r.updatedAt desc, r.id desc
            """)
    List<PostReactionEntity> findActivePage(@Param("postId") Long postId, @Param("cursorUpdatedAt") Instant cursorUpdatedAt, @Param("cursorId") Long cursorId, org.springframework.data.domain.Pageable pageable);

    @Query("""
            select r from PostReactionEntity r where r.postId = :postId and r.reactionType = :reactionType
              and (r.updatedAt < :cursorUpdatedAt or (r.updatedAt = :cursorUpdatedAt and r.id < :cursorId))
            order by r.updatedAt desc, r.id desc
            """)
    List<PostReactionEntity> findActivePageByReactionType(@Param("postId") Long postId, @Param("reactionType") PostReactionType reactionType, @Param("cursorUpdatedAt") Instant cursorUpdatedAt, @Param("cursorId") Long cursorId, org.springframework.data.domain.Pageable pageable);

    @Query("""
            select r.postId as postId, r.reactionType as reactionType, count(r.id) as count
            from PostReactionEntity r
            where r.postId in :postIds
            group by r.postId, r.reactionType
            """)
    List<ReactionCountView> countByPostIds(@Param("postIds") Collection<Long> postIds);
}
