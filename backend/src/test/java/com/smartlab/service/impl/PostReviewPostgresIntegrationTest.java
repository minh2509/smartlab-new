package com.smartlab.service.impl;

import com.smartlab.dto.request.ReviewPostRequest;
import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.entity.PostEntity;
import com.smartlab.entity.PostReviewEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import com.smartlab.enums.ReviewDecision;
import com.smartlab.repo.PostRepository;
import com.smartlab.repo.PostReviewRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.PostService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
class PostReviewPostgresIntegrationTest {

    private static final String TARGET_DATABASE = "smartlab_rich_editor_it";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PostReviewRepository postReviewRepository;
    @Autowired
    private PostService postService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
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
        postIds.forEach(id -> jdbc.update("delete from audit_logs where target_type = 'POST' and target_id = ?", id.toString()));
        postIds.forEach(id -> jdbc.update("delete from posts where id = ?", id));
        userIds.forEach(id -> jdbc.update("delete from tbl_user where id = ?", id));
        postIds.clear();
        userIds.clear();
    }

    @Test
    void reviewEntityPersistsGeneratedIdentityAndHydratesInFreshTransaction() {
        UserFixture author = insertUser("mapping-author");
        UserFixture reviewer = insertUser("mapping-reviewer");
        Long postId = insertPendingReviewPost(author.id(), "mapping");
        Instant createdAt = Instant.parse("2026-08-10T01:23:45.123456Z");
        String exactReason = "  repository mapping evidence  ";

        Long reviewId = inNewTransaction(() -> postReviewRepository.saveAndFlush(PostReviewEntity.create(
                postId,
                reviewer.id(),
                ReviewDecision.APPROVED,
                exactReason,
                createdAt
        )).getId());

        PostReviewSnapshot hydrated = inNewTransaction(() -> {
            PostReviewEntity review = postReviewRepository.findById(reviewId).orElseThrow();
            return new PostReviewSnapshot(
                    review.getId(),
                    review.getPostId(),
                    review.getReviewerUserId(),
                    review.getDecision(),
                    review.getReason(),
                    review.getCreatedAt()
            );
        });

        assertThat(reviewId).isPositive();
        assertThat(hydrated.id()).isEqualTo(reviewId);
        assertThat(hydrated.postId()).isEqualTo(postId);
        assertThat(hydrated.reviewerUserId()).isEqualTo(reviewer.id());
        assertThat(hydrated.decision()).isEqualTo(ReviewDecision.APPROVED);
        assertThat(hydrated.reason()).isEqualTo(exactReason);
        assertThat(hydrated.createdAt()).isEqualTo(createdAt);
    }

    @ParameterizedTest(name = "{0} review commits Post and history")
    @MethodSource("successfulReviewCases")
    void reviewServiceCommitsPostAndHistoryWithOneDatabaseTimestamp(SuccessfulReviewCase reviewCase) {
        UserFixture author = insertUser(reviewCase.label() + "-author");
        UserFixture reviewer = insertUser(reviewCase.label() + "-reviewer");
        Long postId = insertPendingReviewPost(author.id(), reviewCase.label());
        when(userRepository.findByEmail(reviewer.email())).thenReturn(Optional.of(reviewer.entity()));

        PostDetailResponse response = postService.reviewPost(
                reviewer.email(),
                postId,
                new ReviewPostRequest(reviewCase.decision(), reviewCase.reason())
        );

        ReviewSnapshot persisted = inNewTransaction(() -> jdbc.queryForObject("""
                select p.status, p.updated_at, p.published_at, r.id, r.reviewer_user_id,
                       r.decision, r.reason, r.created_at
                from posts p
                join post_reviews r on r.post_id = p.id
                where p.id = ?
                """, (rs, rowNum) -> new ReviewSnapshot(
                PostStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(),
                rs.getLong("id"),
                rs.getLong("reviewer_user_id"),
                ReviewDecision.valueOf(rs.getString("decision")),
                rs.getString("reason"),
                rs.getTimestamp("created_at").toInstant()
        ), postId));

        assertThat(response.getStatus()).isEqualTo(reviewCase.expectedStatus());
        assertThat(inNewTransaction(() -> jdbc.queryForObject(
                "select count(*) from post_reviews where post_id = ?",
                Long.class,
                postId
        ))).isEqualTo(1L);
        assertThat(persisted.status()).isEqualTo(reviewCase.expectedStatus());
        assertThat(persisted.reviewId()).isPositive();
        assertThat(persisted.reviewerUserId()).isEqualTo(reviewer.id());
        assertThat(persisted.decision()).isEqualTo(reviewCase.decision());
        assertThat(persisted.reason()).isEqualTo(reviewCase.reason());
        assertThat(persisted.postUpdatedAt()).isEqualTo(persisted.reviewCreatedAt());
        if (reviewCase.decision() == ReviewDecision.APPROVED) {
            assertThat(persisted.postPublishedAt()).isEqualTo(persisted.reviewCreatedAt());
            assertThat(response.getPublishedAt()).isEqualTo(persisted.reviewCreatedAt());
        } else {
            assertThat(persisted.postPublishedAt()).isNull();
            assertThat(response.getPublishedAt()).isNull();
        }
    }

    @Test
    void reviewerForeignKeyFailureRollsBackManagedPostTransitionAndReviewInsert() {
        UserFixture author = insertUser("rollback-author");
        Long postId = insertPendingReviewPost(author.id(), "rollback");
        PostBeforeState before = inNewTransaction(() -> jdbc.queryForObject("""
                select status, updated_at
                from posts
                where id = ?
                """, (rs, rowNum) -> new PostBeforeState(
                PostStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("updated_at").toInstant()
        ), postId));
        Long phantomReviewerId = jdbc.queryForObject(
                "select coalesce(max(id), 0) + 1000000 from tbl_user",
                Long.class
        );
        assertThat(phantomReviewerId).isNotNull();
        assertThat(jdbc.queryForObject(
                "select count(*) from tbl_user where id = ?",
                Long.class,
                phantomReviewerId
        )).isZero();

        String phantomEmail = "t10-phantom-" + UUID.randomUUID() + "@example.test";
        UserEntity phantomReviewer = UserEntity.builder()
                .id(phantomReviewerId)
                .userId("t10phantom" + UUID.randomUUID().toString().replace("-", ""))
                .name("T10 phantom reviewer")
                .email(phantomEmail)
                .password("t10-integration-only")
                .isActive(true)
                .isAccountVerified(true)
                .resetOtpExpireAt(0L)
                .build();
        when(userRepository.findByEmail(phantomEmail)).thenReturn(Optional.of(phantomReviewer));

        assertThat(before.status()).isEqualTo(PostStatus.PENDING_REVIEW);
        assertThat(inNewTransaction(() -> jdbc.queryForObject(
                "select count(*) from post_reviews where post_id = ?",
                Long.class,
                postId
        ))).isZero();

        assertThatThrownBy(() -> postService.reviewPost(
                phantomEmail,
                postId,
                new ReviewPostRequest(ReviewDecision.APPROVED, "rollback evidence")
        )).isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(exception -> assertThat(postgresConstraint(exception))
                        .isEqualTo("fk_post_reviews_reviewer"));

        RollbackSnapshot after = inNewTransaction(() -> jdbc.queryForObject("""
                select p.status, p.updated_at,
                       (select count(*) from post_reviews r where r.post_id = p.id) as review_count
                from posts p
                where p.id = ?
                """, (rs, rowNum) -> new RollbackSnapshot(
                PostStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("review_count")
        ), postId));

        assertThat(after.status()).isEqualTo(PostStatus.PENDING_REVIEW);
        assertThat(after.updatedAt()).isEqualTo(before.updatedAt());
        assertThat(after.reviewCount()).isZero();
    }

    private UserFixture insertUser(String tag) {
        String marker = UUID.randomUUID().toString();
        String email = "t10-" + tag + "-" + marker + "@example.test";
        Long id = jdbc.queryForObject("""
                insert into tbl_user (
                    user_id, name, email, password, is_active,
                    is_account_verified, reset_otp_expire_at
                ) values (?, ?, ?, ?, true, true, 0)
                returning id
                """, Long.class,
                "t10" + marker.replace("-", ""),
                "T10 " + tag,
                email,
                "t10-integration-only"
        );
        assertThat(id).isNotNull();
        userIds.add(id);
        UserEntity user = UserEntity.builder()
                .id(id)
                .userId("t10" + marker.replace("-", ""))
                .name("T10 " + tag)
                .email(email)
                .password("t10-integration-only")
                .isActive(true)
                .isAccountVerified(true)
                .resetOtpExpireAt(0L)
                .build();
        return new UserFixture(id, email, user);
    }

    private Long insertPendingReviewPost(Long authorId, String tag) {
        Instant createdAt = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS);
        Instant submittedAt = createdAt.plusSeconds(30);
        Long id = inNewTransaction(() -> {
            PostEntity post = PostEntity.createDraft(
                    authorId,
                    "T10 " + tag + " " + UUID.randomUUID(),
                    "t10-" + tag + "-" + UUID.randomUUID(),
                    "T10 PostgreSQL review acceptance",
                    Map.of("type", "doc"),
                    PostVisibility.LAB,
                    null,
                    createdAt
            );
            post.submitForReview(submittedAt);
            return postRepository.saveAndFlush(post).getId();
        });
        assertThat(id).isNotNull();
        postIds.add(id);
        return id;
    }

    private <T> T inNewTransaction(java.util.function.Supplier<T> work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> work.get());
    }

    private static String postgresConstraint(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof PSQLException postgres
                    && postgres.getServerErrorMessage() != null) {
                return postgres.getServerErrorMessage().getConstraint();
            }
            current = current.getCause();
        }
        return null;
    }

    private static Stream<SuccessfulReviewCase> successfulReviewCases() {
        return Stream.of(
                new SuccessfulReviewCase(
                        "approved",
                        ReviewDecision.APPROVED,
                        PostStatus.PUBLISHED,
                        "  approved evidence  "
                ),
                new SuccessfulReviewCase(
                        "revision-required",
                        ReviewDecision.REVISION_REQUIRED,
                        PostStatus.REVISION_REQUIRED,
                        "  revision evidence  "
                ),
                new SuccessfulReviewCase(
                        "rejected",
                        ReviewDecision.REJECTED,
                        PostStatus.REJECTED,
                        "  needs evidence  "
                )
        );
    }

    private String currentDatabase() {
        return jdbc.queryForObject("select current_database()", String.class);
    }

    private record UserFixture(Long id, String email, UserEntity entity) {
    }

    private record SuccessfulReviewCase(
            String label,
            ReviewDecision decision,
            PostStatus expectedStatus,
            String reason
    ) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record PostReviewSnapshot(
            Long id,
            Long postId,
            Long reviewerUserId,
            ReviewDecision decision,
            String reason,
            Instant createdAt
    ) {
    }

    private record PostBeforeState(PostStatus status, Instant updatedAt) {
    }

    private record RollbackSnapshot(PostStatus status, Instant updatedAt, Long reviewCount) {
    }

    private record ReviewSnapshot(
            PostStatus status,
            Instant postUpdatedAt,
            Instant postPublishedAt,
            Long reviewId,
            Long reviewerUserId,
            ReviewDecision decision,
            String reason,
            Instant reviewCreatedAt
    ) {
    }
}
