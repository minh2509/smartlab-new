package com.smartlab.service.impl;

import com.smartlab.dto.request.ReviewPostRequest;
import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.entity.PostEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import com.smartlab.enums.ReviewDecision;
import com.smartlab.repo.NotificationRepository;
import com.smartlab.repo.PostRepository;
import com.smartlab.repo.PostReviewRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.NotificationService;
import com.smartlab.service.NotificationRelated;
import com.smartlab.service.PostService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class PostReviewNotificationPostgresIntegrationTest {

    private static final String TARGET_DATABASE = "smartlab_rich_editor_it";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PostReviewRepository postReviewRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private PostService postService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private UserRepository userRepository;
    @MockitoSpyBean
    private NotificationService notificationService;

    private final Set<Long> postIds = new LinkedHashSet<>();
    private final Set<Long> userIds = new LinkedHashSet<>();
    private DatabaseCounts baselineCounts;

    @BeforeEach
    void requireExactIntegrationDatabaseAndSchema() {
        assertThat(currentDatabase()).isEqualTo(TARGET_DATABASE);
        assertThat(tableExists("tbl_user")).isTrue();
        assertThat(tableExists("posts")).isTrue();
        assertThat(tableExists("post_reviews")).isTrue();
        assertThat(tableExists("notifications")).isTrue();
        assertThat(postRepository).isNotNull();
        assertThat(postReviewRepository).isNotNull();
        assertThat(notificationRepository).isNotNull();
        baselineCounts = databaseCounts();
    }

    @AfterEach
    void cleanExactFixturesAndRestoreBaseline() {
        assertThat(currentDatabase()).isEqualTo(TARGET_DATABASE);
        userIds.forEach(id -> jdbc.update("delete from notifications where recipient_user_id = ?", id));
        postIds.forEach(id -> jdbc.update("delete from posts where id = ?", id));
        userIds.forEach(id -> jdbc.update("delete from tbl_user where id = ?", id));
        postIds.clear();
        userIds.clear();
        assertThat(databaseCounts()).isEqualTo(baselineCounts);
    }

    @ParameterizedTest(name = "{0} review commits matching notification")
    @MethodSource("authoredReviewCases")
    void authoredReviewCommitsPostHistoryAndExactNotification(AuthoredReviewCase reviewCase) {
        UserFixture author = insertUser(reviewCase.label() + "-author");
        UserFixture reviewer = insertUser(reviewCase.label() + "-reviewer");
        Long postId = insertPendingReviewPost(author.id(), reviewCase.label());
        when(userRepository.findByEmail(reviewer.email())).thenReturn(Optional.of(reviewer.entity()));

        PostDetailResponse response = postService.reviewPost(
                reviewer.email(),
                postId,
                new ReviewPostRequest(reviewCase.decision(), reviewCase.reason())
        );

        PostReviewSnapshot persisted = inNewTransaction(() -> readPostReview(postId));
        NotificationSnapshot notification = inNewTransaction(() -> readOnlyNotification(author.id()));

        assertThat(response.getStatus()).isEqualTo(reviewCase.expectedStatus());
        assertThat(response.getUpdatedAt()).isEqualTo(persisted.postUpdatedAt());
        assertThat(persisted.status()).isEqualTo(reviewCase.expectedStatus());
        assertThat(persisted.reviewId()).isPositive();
        assertThat(persisted.reviewerUserId()).isEqualTo(reviewer.id());
        assertThat(persisted.decision()).isEqualTo(reviewCase.decision());
        assertThat(persisted.reason()).isEqualTo(reviewCase.reason());
        assertThat(reviewCount(postId)).isEqualTo(1L);

        assertThat(notification.id()).isPositive();
        assertThat(notification.recipientUserId()).isEqualTo(author.id());
        assertThat(notification.message()).isEqualTo(reviewCase.message());
        assertThat(notification.type()).isEqualTo("POST_REVIEW_" + reviewCase.decision().name());
        assertThat(notification.actorUserId()).isEqualTo(reviewer.id());
        assertThat(notification.relatedType()).isEqualTo("POST");
        assertThat(notification.relatedId()).isEqualTo(postId);
        assertThat(notification.targetUrl()).isNull();
        assertThat(notification.deletedAt()).isNull();
        assertThat(notification.read()).isFalse();
        assertThat(notificationCount(author.id())).isEqualTo(1L);
        assertThat(notification.createdAt()).isEqualTo(persisted.reviewCreatedAt());
        assertThat(notification.createdAt()).isEqualTo(persisted.postUpdatedAt());

        if (reviewCase.decision() == ReviewDecision.REJECTED) {
            assertThat(persisted.reason()).isEqualTo("  full rejection reason remains in history  ");
            assertThat(notification.message()).doesNotContain("full rejection reason remains in history");
        }
    }

    @Test
    void authorlessReviewCommitsHistoryAndSkipsNotification() {
        UserFixture reviewer = insertUser("authorless-reviewer");
        Long postId = insertPendingReviewPost(null, "authorless");
        when(userRepository.findByEmail(reviewer.email())).thenReturn(Optional.of(reviewer.entity()));

        PostDetailResponse response = postService.reviewPost(
                reviewer.email(),
                postId,
                new ReviewPostRequest(ReviewDecision.APPROVED, null)
        );

        PostReviewSnapshot persisted = inNewTransaction(() -> readPostReview(postId));
        DatabaseCounts after = inNewTransaction(this::databaseCounts);

        assertThat(response.getStatus()).isEqualTo(PostStatus.APPROVED);
        assertThat(persisted.status()).isEqualTo(PostStatus.APPROVED);
        assertThat(persisted.decision()).isEqualTo(ReviewDecision.APPROVED);
        assertThat(reviewCount(postId)).isEqualTo(1L);
        assertThat(after.notifications()).isEqualTo(baselineCounts.notifications());
    }

    @Test
    void notificationProviderFailureRollsBackPostAndReviewHistoryInOuterTransaction() {
        UserFixture author = insertUser("rollback-author");
        UserFixture reviewer = insertUser("rollback-reviewer");
        Long postId = insertPendingReviewPost(author.id(), "rollback");
        when(userRepository.findByEmail(reviewer.email())).thenReturn(Optional.of(reviewer.entity()));
        PostBeforeState before = inNewTransaction(() -> readPostBeforeState(postId));
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("injected notification provider failure");
        doThrow(failure).when(notificationService).notify(
                eq(author.id()),
                eq("POST_REVIEW_APPROVED"),
                eq("Bài viết của bạn đã được duyệt."),
                eq(new NotificationRelated(reviewer.id(), "POST", postId, null)),
                any(Instant.class)
        );

        assertThatThrownBy(() -> postService.reviewPost(
                reviewer.email(),
                postId,
                new ReviewPostRequest(ReviewDecision.APPROVED, "rollback notification evidence")
        )).isSameAs(failure);

        verify(notificationService).notify(
                eq(author.id()),
                eq("POST_REVIEW_APPROVED"),
                eq("Bài viết của bạn đã được duyệt."),
                eq(new NotificationRelated(reviewer.id(), "POST", postId, null)),
                any(Instant.class)
        );
        RollbackSnapshot after = inNewTransaction(() -> readRollbackSnapshot(postId, author.id()));
        assertThat(after.status()).isEqualTo(PostStatus.PENDING_REVIEW);
        assertThat(after.updatedAt()).isEqualTo(before.updatedAt());
        assertThat(after.reviewCount()).isZero();
        assertThat(after.notificationCount()).isZero();
    }

    private UserFixture insertUser(String tag) {
        String marker = UUID.randomUUID().toString();
        String email = "t14c-" + tag + "-" + marker + "@example.test";
        Long id = jdbc.queryForObject("""
                insert into tbl_user (
                    user_id, name, email, password, is_active,
                    is_account_verified, reset_otp_expire_at
                ) values (?, ?, ?, ?, true, true, 0)
                returning id
                """, Long.class,
                "t14c" + marker.replace("-", ""),
                "T14C " + tag,
                email,
                "t14c-integration-only"
        );
        assertThat(id).isNotNull();
        userIds.add(id);
        UserEntity user = UserEntity.builder()
                .id(id)
                .userId("t14c" + marker.replace("-", ""))
                .name("T14C " + tag)
                .email(email)
                .password("t14c-integration-only")
                .isActive(true)
                .isAccountVerified(true)
                .resetOtpExpireAt(0L)
                .build();
        return new UserFixture(id, email, user);
    }

    private Long insertPendingReviewPost(Long authorId, String tag) {
        Instant createdAt = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS);
        Long id = inNewTransaction(() -> {
            PostEntity post = PostEntity.createDraft(
                    authorId,
                    "T14C " + tag + " " + UUID.randomUUID(),
                    "t14c-" + tag + "-" + UUID.randomUUID(),
                    "T14C notification acceptance",
                    Map.of("type", "doc"),
                    PostVisibility.LAB,
                    null,
                    createdAt
            );
            post.submitForReview(createdAt.plusSeconds(30));
            return postRepository.saveAndFlush(post).getId();
        });
        assertThat(id).isNotNull();
        postIds.add(id);
        return id;
    }

    private PostReviewSnapshot readPostReview(Long postId) {
        return jdbc.queryForObject("""
                select p.status, p.updated_at, r.id, r.reviewer_user_id,
                       r.decision, r.reason, r.created_at
                from posts p
                join post_reviews r on r.post_id = p.id
                where p.id = ?
                """, (rs, rowNum) -> new PostReviewSnapshot(
                PostStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("id"),
                rs.getLong("reviewer_user_id"),
                ReviewDecision.valueOf(rs.getString("decision")),
                rs.getString("reason"),
                rs.getTimestamp("created_at").toInstant()
        ), postId);
    }

    private NotificationSnapshot readOnlyNotification(Long recipientUserId) {
        return jdbc.queryForObject("""
                select id, recipient_user_id, actor_user_id, type, message, related_type, related_id,
                       target_url, is_read, deleted_at, created_at
                from notifications
                where recipient_user_id = ?
                """, (rs, rowNum) -> new NotificationSnapshot(
                rs.getLong("id"),
                rs.getLong("recipient_user_id"),
                rs.getLong("actor_user_id"),
                rs.getString("type"),
                rs.getString("message"),
                rs.getString("related_type"),
                rs.getLong("related_id"),
                rs.getString("target_url"),
                rs.getBoolean("is_read"),
                rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant(),
                rs.getTimestamp("created_at").toInstant()
        ), recipientUserId);
    }

    private PostBeforeState readPostBeforeState(Long postId) {
        return jdbc.queryForObject("select status, updated_at from posts where id = ?", (rs, rowNum) ->
                new PostBeforeState(
                        PostStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("updated_at").toInstant()
                ), postId);
    }

    private RollbackSnapshot readRollbackSnapshot(Long postId, Long recipientUserId) {
        return jdbc.queryForObject("""
                select p.status, p.updated_at,
                       (select count(*) from post_reviews r where r.post_id = p.id) as review_count,
                       (select count(*) from notifications n where n.recipient_user_id = ?) as notification_count
                from posts p
                where p.id = ?
                """, (rs, rowNum) -> new RollbackSnapshot(
                PostStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("review_count"),
                rs.getLong("notification_count")
        ), recipientUserId, postId);
    }

    private long reviewCount(Long postId) {
        return inNewTransaction(() -> jdbc.queryForObject(
                "select count(*) from post_reviews where post_id = ?",
                Long.class,
                postId
        ));
    }

    private long notificationCount(Long recipientUserId) {
        return inNewTransaction(() -> jdbc.queryForObject(
                "select count(*) from notifications where recipient_user_id = ?",
                Long.class,
                recipientUserId
        ));
    }

    private DatabaseCounts databaseCounts() {
        return new DatabaseCounts(
                jdbc.queryForObject("select count(*) from tbl_user", Long.class),
                jdbc.queryForObject("select count(*) from posts", Long.class),
                jdbc.queryForObject("select count(*) from post_reviews", Long.class),
                jdbc.queryForObject("select count(*) from notifications", Long.class)
        );
    }

    private String currentDatabase() {
        return jdbc.queryForObject("select current_database()", String.class);
    }

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select to_regclass('public.' || ?) is not null",
                Boolean.class,
                tableName
        ));
    }

    private <T> T inNewTransaction(java.util.function.Supplier<T> work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> work.get());
    }

    private static Stream<AuthoredReviewCase> authoredReviewCases() {
        return Stream.of(
                new AuthoredReviewCase(
                        "approved",
                        ReviewDecision.APPROVED,
                        PostStatus.APPROVED,
                        "  approval reason stays in history  ",
                        "Bài viết của bạn đã được duyệt."
                ),
                new AuthoredReviewCase(
                        "revision-required",
                        ReviewDecision.REVISION_REQUIRED,
                        PostStatus.REVISION_REQUIRED,
                        "  revision reason stays in history  ",
                        "Bài viết của bạn cần được chỉnh sửa."
                ),
                new AuthoredReviewCase(
                        "rejected",
                        ReviewDecision.REJECTED,
                        PostStatus.REJECTED,
                        "  full rejection reason remains in history  ",
                        "Bài viết của bạn đã bị từ chối."
                )
        );
    }

    private record UserFixture(Long id, String email, UserEntity entity) {
    }

    private record AuthoredReviewCase(
            String label,
            ReviewDecision decision,
            PostStatus expectedStatus,
            String reason,
            String message
    ) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record PostReviewSnapshot(
            PostStatus status,
            Instant postUpdatedAt,
            Long reviewId,
            Long reviewerUserId,
            ReviewDecision decision,
            String reason,
            Instant reviewCreatedAt
    ) {
    }

    private record NotificationSnapshot(
            Long id,
            Long recipientUserId,
            Long actorUserId,
            String type,
            String message,
            String relatedType,
            Long relatedId,
            String targetUrl,
            boolean read,
            Instant deletedAt,
            Instant createdAt
    ) {
    }

    private record PostBeforeState(PostStatus status, Instant updatedAt) {
    }

    private record RollbackSnapshot(
            PostStatus status,
            Instant updatedAt,
            long reviewCount,
            long notificationCount
    ) {
    }

    private record DatabaseCounts(long users, long posts, long reviews, long notifications) {
    }
}
