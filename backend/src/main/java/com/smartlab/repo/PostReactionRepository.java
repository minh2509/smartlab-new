package com.smartlab.repo;

import com.smartlab.entity.PostReactionEntity;
import com.smartlab.enums.PostReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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
            select r.postId as postId, r.reactionType as reactionType, count(r.id) as count
            from PostReactionEntity r
            where r.postId in :postIds
            group by r.postId, r.reactionType
            """)
    List<ReactionCountView> countByPostIds(@Param("postIds") Collection<Long> postIds);
}
