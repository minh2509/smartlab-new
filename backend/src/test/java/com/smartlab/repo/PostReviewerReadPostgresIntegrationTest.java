package com.smartlab.repo;

import com.smartlab.entity.PostEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import com.smartlab.enums.ReviewDecision;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PostReviewerReadPostgresIntegrationTest {

    private static final String TARGET_DATABASE = "smartlab_rich_editor_it";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private UserRepository userRepository;

    private final Set<Long> postIds = new LinkedHashSet<>();
    private final Set<Long> userIds = new LinkedHashSet<>();

    @BeforeEach
    void requireExactIntegrationDatabase() {
        assertThat(currentDatabase()).isEqualTo(TARGET_DATABASE);
    }

    @AfterEach
    void cleanExactFixtures() {
        assertThat(currentDatabase()).isEqualTo(TARGET_DATABASE);
        postIds.forEach(id -> jdbc.update("delete from posts where id = ?", id));
        userIds.forEach(id -> jdbc.update("delete from tbl_user where id = ?", id));
        postIds.clear();
        userIds.clear();
    }

    @Test
    void reviewerListReturnsOnlyActiveNonSelfPendingPostsAcrossAllVisibilitiesInExactOrder() {
        UserEntity reviewer = insertUser("list-reviewer");
        UserEntity author = insertUser("list-author");
        Instant base = Instant.parse("2026-08-11T00:00:00Z");

        PostEntity projectOlder = insertPost(author.getId(), PostStatus.PENDING_REVIEW,
                PostVisibility.PROJECT, base.plusSeconds(1), "project-older");
        PostEntity labTieOlderId = insertPost(author.getId(), PostStatus.PENDING_REVIEW,
                PostVisibility.LAB, base.plusSeconds(2), "lab-tie");
        PostEntity publicTieNewerId = insertPost(author.getId(), PostStatus.PENDING_REVIEW,
                PostVisibility.PUBLIC, base.plusSeconds(2), "public-tie");
        PostEntity authorlessNewest = insertPost(null, PostStatus.PENDING_REVIEW,
                PostVisibility.PROJECT, base.plusSeconds(3), "authorless-newest");

        insertPost(reviewer.getId(), PostStatus.PENDING_REVIEW,
                PostVisibility.PUBLIC, base.plusSeconds(20), "self-pending");
        for (PostStatus status : nonPendingStatuses()) {
            insertPost(author.getId(), status, PostVisibility.LAB,
                    base.plusSeconds(30 + status.ordinal()), "non-pending-" + status.name().toLowerCase());
        }
        PostEntity deletedPending = insertPost(author.getId(), PostStatus.PENDING_REVIEW,
                PostVisibility.PUBLIC, base.plusSeconds(40), "deleted-pending");
        markDeleted(deletedPending.getId(), base.plusSeconds(41));

        List<PostEntity> result = postRepository
                .findActivePendingReviewableByReviewerUserId(reviewer.getId());

        assertThat(result).extracting(PostEntity::getId).containsExactly(
                authorlessNewest.getId(),
                publicTieNewerId.getId(),
                labTieOlderId.getId(),
                projectOlder.getId()
        );
        assertThat(result).extracting(PostEntity::getVisibility).containsExactly(
                PostVisibility.PROJECT,
                PostVisibility.PUBLIC,
                PostVisibility.LAB,
                PostVisibility.PROJECT
        );
    }

    @Test
    void reviewerDetailUsesTheSamePredicateForOtherAuthorAuthorlessSelfDeletedAndEveryStatus() {
        UserEntity reviewer = insertUser("detail-reviewer");
        UserEntity author = insertUser("detail-author");
        Instant base = Instant.parse("2026-08-11T01:00:00Z");

        List<PostEntity> reviewable = List.of(
                insertPost(author.getId(), PostStatus.PENDING_REVIEW,
                        PostVisibility.PUBLIC, base.plusSeconds(1), "detail-public"),
                insertPost(author.getId(), PostStatus.PENDING_REVIEW,
                        PostVisibility.LAB, base.plusSeconds(2), "detail-lab"),
                insertPost(author.getId(), PostStatus.PENDING_REVIEW,
                        PostVisibility.PROJECT, base.plusSeconds(3), "detail-project"),
                insertPost(null, PostStatus.PENDING_REVIEW,
                        PostVisibility.LAB, base.plusSeconds(4), "detail-authorless")
        );
        PostEntity selfPending = insertPost(reviewer.getId(), PostStatus.PENDING_REVIEW,
                PostVisibility.LAB, base.plusSeconds(5), "detail-self");
        PostEntity deletedPending = insertPost(author.getId(), PostStatus.PENDING_REVIEW,
                PostVisibility.LAB, base.plusSeconds(6), "detail-deleted");
        markDeleted(deletedPending.getId(), base.plusSeconds(7));

        List<PostEntity> nonPending = new ArrayList<>();
        for (PostStatus status : nonPendingStatuses()) {
            nonPending.add(insertPost(author.getId(), status, PostVisibility.LAB,
                    base.plusSeconds(10 + status.ordinal()), "detail-" + status.name().toLowerCase()));
        }

        reviewable.forEach(post -> assertThat(reviewableDetail(post.getId(), reviewer.getId()))
                .map(PostEntity::getId)
                .contains(post.getId()));
        assertThat(reviewableDetail(selfPending.getId(), reviewer.getId())).isEmpty();
        assertThat(reviewableDetail(deletedPending.getId(), reviewer.getId())).isEmpty();
        nonPending.forEach(post -> assertThat(reviewableDetail(post.getId(), reviewer.getId())).isEmpty());
        assertThat(reviewableDetail(Long.MAX_VALUE, reviewer.getId())).isEmpty();
    }

    private UserEntity insertUser(String tag) {
        String marker = UUID.randomUUID().toString();
        UserEntity user = userRepository.saveAndFlush(UserEntity.builder()
                .userId("rr-" + marker.replace("-", ""))
                .name("Reviewer read " + tag)
                .email("review-read-" + tag + "-" + marker + "@example.test")
                .password("")
                .isActive(true)
                .isAccountVerified(true)
                .resetOtpExpireAt(0L)
                .build());
        userIds.add(user.getId());
        return user;
    }

    private PostEntity insertPost(
            Long authorId,
            PostStatus status,
            PostVisibility visibility,
            Instant createdAt,
            String tag
    ) {
        String marker = UUID.randomUUID().toString();
        Instant exactCreatedAt = createdAt.truncatedTo(ChronoUnit.MICROS);
        PostEntity post = PostEntity.createDraft(
                authorId,
                "Reviewer read " + tag,
                "review-read-" + tag + "-" + marker,
                "Reviewer read PostgreSQL acceptance",
                Map.of("type", "doc"),
                visibility,
                null,
                exactCreatedAt
        );
        moveToStatus(post, status, exactCreatedAt.plusSeconds(1));
        PostEntity saved = postRepository.saveAndFlush(post);
        postIds.add(saved.getId());
        return saved;
    }

    private static void moveToStatus(PostEntity post, PostStatus status, Instant mutationInstant) {
        switch (status) {
            case DRAFT -> {
            }
            case PENDING_REVIEW -> post.submitForReview(mutationInstant);
            case REVISION_REQUIRED -> {
                post.submitForReview(mutationInstant);
                post.applyReviewDecision(ReviewDecision.REVISION_REQUIRED, mutationInstant.plusSeconds(1));
            }
            case APPROVED -> {
                post.submitForReview(mutationInstant);
                setStatus(post, PostStatus.APPROVED);
            }
            case PUBLISHED -> {
                post.submitForReview(mutationInstant);
                post.applyReviewDecision(ReviewDecision.APPROVED, mutationInstant.plusSeconds(1));
            }
            case REJECTED -> {
                post.submitForReview(mutationInstant);
                post.applyReviewDecision(ReviewDecision.REJECTED, mutationInstant.plusSeconds(1));
            }
        }
    }

    private static void setStatus(PostEntity post, PostStatus status) {
        try {
            java.lang.reflect.Field field = PostEntity.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(post, status);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private void markDeleted(Long postId, Instant deletedAt) {
        assertThat(jdbc.update(
                "update posts set deleted_at = ?, updated_at = ? where id = ?",
                Timestamp.from(deletedAt),
                Timestamp.from(deletedAt),
                postId
        )).isOne();
    }

    private java.util.Optional<PostEntity> reviewableDetail(Long postId, Long reviewerId) {
        return postRepository.findActivePendingReviewableByIdAndReviewerUserId(postId, reviewerId);
    }

    private static List<PostStatus> nonPendingStatuses() {
        return java.util.Arrays.stream(PostStatus.values())
                .filter(status -> status != PostStatus.PENDING_REVIEW)
                .toList();
    }

    private String currentDatabase() {
        return jdbc.queryForObject("select current_database()", String.class);
    }
}
