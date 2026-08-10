package com.smartlab.service.impl;

import com.smartlab.dto.request.ReviewPostRequest;
import com.smartlab.dto.request.UpdatePostRequest;
import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.entity.PostEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import com.smartlab.enums.ReviewDecision;
import com.smartlab.repo.PostRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.PostService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
class PostWorkflowConcurrencyPostgresIntegrationTest {

    private static final String TARGET_DATABASE = "smartlab_rich_editor_it";
    private static final Duration LOCK_OBSERVATION_TIMEOUT = Duration.ofSeconds(5);
    private static final long FUTURE_TIMEOUT_SECONDS = 10;

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PostService postService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private UserRepository userRepository;

    private final Set<Long> postIds = new LinkedHashSet<>();
    private final Set<Long> userIds = new LinkedHashSet<>();
    private DatabaseCounts baselineCounts;

    @BeforeEach
    void requireExactIntegrationDatabase() {
        assertThat(currentDatabase()).isEqualTo(TARGET_DATABASE);
        baselineCounts = databaseCounts();
    }

    @AfterEach
    void cleanExactFixtures() {
        assertThat(currentDatabase()).isEqualTo(TARGET_DATABASE);
        postIds.forEach(id -> jdbc.update("delete from posts where id = ?", id));
        userIds.forEach(id -> jdbc.update("delete from tbl_user where id = ?", id));
        postIds.clear();
        userIds.clear();
        assertThat(databaseCounts()).isEqualTo(baselineCounts);
    }

    @Test
    void concurrentSubmissionsSerializeToOneSuccessAndOneConflict() throws Exception {
        UserFixture author = insertUser("submit-submit-author");
        PostFixture post = insertPost(author.id(), PostStatus.DRAFT, "original submit excerpt", "submit-submit");
        when(userRepository.findByEmail(author.email())).thenReturn(Optional.of(author.entity()));

        RaceExecution race = raceBehindHeldPostLock(
                post.id(),
                () -> postService.submitForReview(author.email(), post.id()),
                () -> postService.submitForReview(author.email(), post.id())
        );

        assertThat(race.lockObservation().waitingBackendPids()).hasSize(2);
        assertThat(race.outcomes()).filteredOn(CommandOutcome::succeeded).hasSize(1);
        assertThat(race.outcomes()).filteredOn(outcome -> hasStatus(outcome, HttpStatus.CONFLICT)).hasSize(1);

        PostSnapshot persisted = readPost(post.id());
        assertThat(persisted.status()).isEqualTo(PostStatus.PENDING_REVIEW);
        assertThat(persisted.deletedAt()).isNull();
        assertThat(reviewCount(post.id())).isZero();
    }

    @Test
    void concurrentReviewsSerializeToOneHistoryRowMatchingTheWinningDecision() throws Exception {
        UserFixture author = insertUser("review-review-author");
        UserFixture reviewerA = insertUser("review-review-a");
        UserFixture reviewerB = insertUser("review-review-b");
        PostFixture post = insertPost(author.id(), PostStatus.PENDING_REVIEW, "review race", "review-review");
        when(userRepository.findByEmail(reviewerA.email())).thenReturn(Optional.of(reviewerA.entity()));
        when(userRepository.findByEmail(reviewerB.email())).thenReturn(Optional.of(reviewerB.entity()));

        ReviewAttempt approved = new ReviewAttempt(
                reviewerA,
                ReviewDecision.APPROVED,
                "  concurrency approval  ",
                PostStatus.APPROVED
        );
        ReviewAttempt rejected = new ReviewAttempt(
                reviewerB,
                ReviewDecision.REJECTED,
                "  concurrency rejection  ",
                PostStatus.REJECTED
        );

        RaceExecution race = raceBehindHeldPostLock(
                post.id(),
                () -> postService.reviewPost(
                        approved.reviewer().email(),
                        post.id(),
                        new ReviewPostRequest(approved.decision(), approved.reason())
                ),
                () -> postService.reviewPost(
                        rejected.reviewer().email(),
                        post.id(),
                        new ReviewPostRequest(rejected.decision(), rejected.reason())
                )
        );

        assertThat(race.lockObservation().waitingBackendPids()).hasSize(2);
        assertThat(race.outcomes()).filteredOn(CommandOutcome::succeeded).hasSize(1);
        assertThat(race.outcomes()).filteredOn(outcome -> hasStatus(outcome, HttpStatus.CONFLICT)).hasSize(1);

        ReviewAttempt winner = race.first().succeeded() ? approved : rejected;
        PostSnapshot persistedPost = readPost(post.id());
        PersistedReview persistedReview = readOnlyReview(post.id());

        assertThat(reviewCount(post.id())).isEqualTo(1);
        assertThat(persistedPost.status()).isEqualTo(winner.expectedStatus());
        assertThat(persistedReview.decision()).isEqualTo(winner.decision());
        assertThat(persistedReview.reviewerUserId()).isEqualTo(winner.reviewer().id());
        assertThat(persistedReview.reason()).isEqualTo(winner.reason());
        assertThat(persistedPost.updatedAt()).isEqualTo(persistedReview.createdAt());
        System.out.printf("T11_EVIDENCE review-review winner=%s reviewer=%d%n",
                winner.decision(), winner.reviewer().id());
    }

    @Test
    void concurrentPublishesSerializeToOnePublicationTimestamp() throws Exception {
        UserFixture author = insertUser("publish-publish-author");
        UserFixture publisherA = insertUser("publish-publish-a");
        UserFixture publisherB = insertUser("publish-publish-b");
        PostFixture post = insertPost(author.id(), PostStatus.APPROVED, "publish race", "publish-publish");
        when(userRepository.findByEmail(publisherA.email())).thenReturn(Optional.of(publisherA.entity()));
        when(userRepository.findByEmail(publisherB.email())).thenReturn(Optional.of(publisherB.entity()));

        RaceExecution race = raceBehindHeldPostLock(
                post.id(),
                () -> postService.publishPost(publisherA.email(), post.id()),
                () -> postService.publishPost(publisherB.email(), post.id())
        );

        assertThat(race.lockObservation().waitingBackendPids()).hasSize(2);
        assertThat(race.outcomes()).filteredOn(CommandOutcome::succeeded).hasSize(1);
        assertThat(race.outcomes()).filteredOn(outcome -> hasStatus(outcome, HttpStatus.CONFLICT)).hasSize(1);

        PostDetailResponse successfulResponse = (PostDetailResponse) race.outcomes().stream()
                .filter(CommandOutcome::succeeded)
                .findFirst()
                .orElseThrow()
                .value();
        PostSnapshot persisted = readPost(post.id());
        assertThat(persisted.status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(persisted.publishedAt()).isNotNull();
        assertThat(persisted.publishedAt()).isEqualTo(persisted.updatedAt());
        assertThat(successfulResponse.getPublishedAt()).isEqualTo(persisted.publishedAt());
        assertThat(reviewCount(post.id())).isZero();
    }

    @Test
    void concurrentDirectPublishesSerializeToOneSuccessAndOnePublicationTimestamp() throws Exception {
        UserFixture owner = insertUser("direct-direct-owner");
        PostFixture post = insertPost(owner.id(), PostStatus.DRAFT, "direct race", "direct-direct");
        when(userRepository.findByEmail(owner.email())).thenReturn(Optional.of(owner.entity()));

        RaceExecution race = raceBehindHeldPostLock(
                post.id(),
                () -> postService.directPublishPost(owner.email(), post.id()),
                () -> postService.directPublishPost(owner.email(), post.id())
        );

        assertThat(race.lockObservation().waitingBackendPids()).hasSize(2);
        assertThat(race.outcomes()).filteredOn(CommandOutcome::succeeded).hasSize(1);
        assertThat(race.outcomes()).filteredOn(outcome -> hasStatus(outcome, HttpStatus.CONFLICT)).hasSize(1);

        PostDetailResponse successfulResponse = (PostDetailResponse) race.outcomes().stream()
                .filter(CommandOutcome::succeeded)
                .findFirst()
                .orElseThrow()
                .value();
        PostSnapshot persisted = readPost(post.id());
        assertThat(persisted.status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(persisted.publishedAt()).isNotNull().isEqualTo(persisted.updatedAt());
        assertThat(successfulResponse.getPublishedAt()).isEqualTo(persisted.publishedAt());
        assertThat(reviewCount(post.id())).isZero();
    }

    @Test
    void concurrentDirectPublishAndSubmitMatchExactlyOneSerializedWinner() throws Exception {
        UserFixture owner = insertUser("direct-submit-owner");
        PostFixture post = insertPost(owner.id(), PostStatus.DRAFT, "direct submit race", "direct-submit");
        when(userRepository.findByEmail(owner.email())).thenReturn(Optional.of(owner.entity()));

        RaceExecution race = raceBehindHeldPostLock(
                post.id(),
                () -> postService.directPublishPost(owner.email(), post.id()),
                () -> postService.submitForReview(owner.email(), post.id())
        );

        assertThat(race.lockObservation().waitingBackendPids()).hasSize(2);
        assertThat(race.outcomes()).filteredOn(CommandOutcome::succeeded).hasSize(1);
        assertThat(race.outcomes()).filteredOn(outcome -> hasStatus(outcome, HttpStatus.CONFLICT)).hasSize(1);

        PostSnapshot persisted = readPost(post.id());
        if (persisted.status() == PostStatus.PUBLISHED) {
            assertThat(race.first().succeeded()).isTrue();
            assertThat(hasStatus(race.second(), HttpStatus.CONFLICT)).isTrue();
            assertThat(persisted.publishedAt()).isNotNull().isEqualTo(persisted.updatedAt());
            System.out.println("T11A4_EVIDENCE direct-submit history=DIRECT_THEN_SUBMIT_CONFLICT");
        } else {
            assertThat(persisted.status()).isEqualTo(PostStatus.PENDING_REVIEW);
            assertThat(hasStatus(race.first(), HttpStatus.CONFLICT)).isTrue();
            assertThat(race.second().succeeded()).isTrue();
            assertThat(persisted.publishedAt()).isNull();
            System.out.println("T11A4_EVIDENCE direct-submit history=SUBMIT_THEN_DIRECT_CONFLICT");
        }
        assertThat(reviewCount(post.id())).isZero();
    }

    @Test
    void concurrentDirectPublishAndPatchMatchAValidSerializedHistory() throws Exception {
        UserFixture owner = insertUser("direct-patch-owner");
        String originalExcerpt = "original direct patch excerpt";
        String patchedExcerpt = "patched before direct publication";
        PostFixture post = insertPost(owner.id(), PostStatus.DRAFT, originalExcerpt, "direct-patch");
        when(userRepository.findByEmail(owner.email())).thenReturn(Optional.of(owner.entity()));
        UpdatePostRequest patch = new UpdatePostRequest();
        patch.setExcerpt(patchedExcerpt);

        RaceExecution race = raceBehindHeldPostLock(
                post.id(),
                () -> postService.directPublishPost(owner.email(), post.id()),
                () -> postService.updatePost(owner.email(), post.id(), patch)
        );

        assertThat(race.lockObservation().waitingBackendPids()).hasSize(2);
        assertThat(race.first().succeeded()).isTrue();
        assertThat(race.second().succeeded() || hasStatus(race.second(), HttpStatus.CONFLICT)).isTrue();

        PostSnapshot persisted = readPost(post.id());
        assertThat(persisted.status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(persisted.publishedAt()).isNotNull().isEqualTo(persisted.updatedAt());
        if (race.second().succeeded()) {
            assertThat(persisted.excerpt()).isEqualTo(patchedExcerpt);
            System.out.println("T11A4_EVIDENCE direct-patch history=PATCH_THEN_DIRECT");
        } else {
            assertThat(persisted.excerpt()).isEqualTo(originalExcerpt);
            System.out.println("T11A4_EVIDENCE direct-patch history=DIRECT_THEN_PATCH_CONFLICT");
        }
        assertThat(reviewCount(post.id())).isZero();
    }

    @Test
    void concurrentDirectPublishAndDeleteMatchExactlyOneSerializedWinner() throws Exception {
        UserFixture owner = insertUser("direct-delete-owner");
        PostFixture post = insertPost(owner.id(), PostStatus.DRAFT, "direct delete race", "direct-delete");
        when(userRepository.findByEmail(owner.email())).thenReturn(Optional.of(owner.entity()));

        RaceExecution race = raceBehindHeldPostLock(
                post.id(),
                () -> postService.directPublishPost(owner.email(), post.id()),
                () -> {
                    postService.deletePost(owner.email(), post.id());
                    return null;
                }
        );

        assertThat(race.lockObservation().waitingBackendPids()).hasSize(2);
        assertThat(race.outcomes()).filteredOn(CommandOutcome::succeeded).hasSize(1);

        PostSnapshot persisted = readPost(post.id());
        if (persisted.deletedAt() == null) {
            assertThat(race.first().succeeded()).isTrue();
            assertThat(hasStatus(race.second(), HttpStatus.CONFLICT)).isTrue();
            assertThat(persisted.status()).isEqualTo(PostStatus.PUBLISHED);
            assertThat(persisted.publishedAt()).isNotNull().isEqualTo(persisted.updatedAt());
            System.out.println("T11A4_EVIDENCE direct-delete history=DIRECT_THEN_DELETE_CONFLICT");
        } else {
            assertThat(race.second().succeeded()).isTrue();
            assertThat(hasStatus(race.first(), HttpStatus.NOT_FOUND)).isTrue();
            assertThat(persisted.status()).isEqualTo(PostStatus.DRAFT);
            assertThat(persisted.publishedAt()).isNull();
            System.out.println("T11A4_EVIDENCE direct-delete history=DELETE_THEN_DIRECT_NOT_FOUND");
        }
        assertThat(reviewCount(post.id())).isZero();
    }

    @Test
    void concurrentSubmitAndPatchMatchAValidSerializedHistory() throws Exception {
        UserFixture author = insertUser("submit-patch-author");
        String originalExcerpt = "original patch excerpt";
        String patchedExcerpt = "patched by concurrent PATCH";
        PostFixture post = insertPost(author.id(), PostStatus.DRAFT, originalExcerpt, "submit-patch");
        when(userRepository.findByEmail(author.email())).thenReturn(Optional.of(author.entity()));
        UpdatePostRequest patch = new UpdatePostRequest();
        patch.setExcerpt(patchedExcerpt);

        RaceExecution race = raceBehindHeldPostLock(
                post.id(),
                () -> postService.submitForReview(author.email(), post.id()),
                () -> postService.updatePost(author.email(), post.id(), patch)
        );

        assertThat(race.lockObservation().waitingBackendPids()).hasSize(2);
        assertThat(race.first().succeeded()).isTrue();
        assertThat(race.second().succeeded() || hasStatus(race.second(), HttpStatus.CONFLICT)).isTrue();

        PostSnapshot persisted = readPost(post.id());
        assertThat(persisted.status()).isEqualTo(PostStatus.PENDING_REVIEW);
        assertThat(persisted.deletedAt()).isNull();
        if (race.second().succeeded()) {
            assertThat(persisted.excerpt()).isEqualTo(patchedExcerpt);
        } else {
            assertThat(persisted.excerpt()).isEqualTo(originalExcerpt);
        }
        assertThat(reviewCount(post.id())).isZero();
        System.out.printf("T11_EVIDENCE submit-patch history=%s%n",
                race.second().succeeded() ? "PATCH_THEN_SUBMIT" : "SUBMIT_THEN_PATCH_CONFLICT");
    }

    @Test
    void concurrentSubmitAndDeleteMatchExactlyOneSerializedWinner() throws Exception {
        UserFixture author = insertUser("submit-delete-author");
        PostFixture post = insertPost(author.id(), PostStatus.DRAFT, "submit delete race", "submit-delete");
        when(userRepository.findByEmail(author.email())).thenReturn(Optional.of(author.entity()));

        RaceExecution race = raceBehindHeldPostLock(
                post.id(),
                () -> postService.submitForReview(author.email(), post.id()),
                () -> {
                    postService.deletePost(author.email(), post.id());
                    return null;
                }
        );

        assertThat(race.lockObservation().waitingBackendPids()).hasSize(2);
        assertThat(race.outcomes()).filteredOn(CommandOutcome::succeeded).hasSize(1);
        PostSnapshot persisted = readPost(post.id());
        if (persisted.deletedAt() == null) {
            assertThat(race.first().succeeded()).isTrue();
            assertThat(hasStatus(race.second(), HttpStatus.CONFLICT)).isTrue();
            assertThat(persisted.status()).isEqualTo(PostStatus.PENDING_REVIEW);
        } else {
            assertThat(race.second().succeeded()).isTrue();
            assertThat(hasStatus(race.first(), HttpStatus.NOT_FOUND)).isTrue();
            assertThat(persisted.status()).isEqualTo(PostStatus.DRAFT);
        }
        assertThat(reviewCount(post.id())).isZero();
        System.out.printf("T11_EVIDENCE submit-delete history=%s%n",
                persisted.deletedAt() == null ? "SUBMIT_THEN_DELETE_CONFLICT" : "DELETE_THEN_SUBMIT_NOT_FOUND");
    }

    private RaceExecution raceBehindHeldPostLock(
            Long postId,
            Callable<?> firstCommand,
            Callable<?> secondCommand
    ) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch startWorkers = new CountDownLatch(1);

        try (Connection gate = dataSource.getConnection()) {
            assertTargetDatabase(gate);
            gate.setAutoCommit(false);
            int gateBackendPid = backendPid(gate);
            lockPost(gate, postId);

            Future<CommandOutcome> first = workers.submit(
                    () -> executeAfterBarrier(firstCommand, workersReady, startWorkers)
            );
            Future<CommandOutcome> second = workers.submit(
                    () -> executeAfterBarrier(secondCommand, workersReady, startWorkers)
            );

            assertThat(workersReady.await(5, TimeUnit.SECONDS)).isTrue();
            startWorkers.countDown();

            LockObservation lockObservation = null;
            Throwable observationFailure = null;
            try {
                lockObservation = awaitTwoLockWaiters(gateBackendPid);
            } catch (Throwable failure) {
                observationFailure = failure;
            } finally {
                gate.commit();
            }

            CommandOutcome firstOutcome = first.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            CommandOutcome secondOutcome = second.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (observationFailure != null) {
                throw new AssertionError("Did not observe two concurrent PostgreSQL lock waiters", observationFailure);
            }
            System.out.printf("T11A4_LOCK_EVIDENCE gate=%d waiters=%s%n",
                    lockObservation.gateBackendPid(), lockObservation.waitingBackendPids());
            return new RaceExecution(firstOutcome, secondOutcome, lockObservation);
        } finally {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private CommandOutcome executeAfterBarrier(
            Callable<?> command,
            CountDownLatch workersReady,
            CountDownLatch startWorkers
    ) {
        workersReady.countDown();
        try {
            if (!startWorkers.await(5, TimeUnit.SECONDS)) {
                return CommandOutcome.failure(new AssertionError("Worker start barrier timed out"));
            }
            return CommandOutcome.success(command.call());
        } catch (Throwable failure) {
            return CommandOutcome.failure(failure);
        }
    }

    private LockObservation awaitTwoLockWaiters(int gateBackendPid) {
        long deadline = System.nanoTime() + LOCK_OBSERVATION_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Set<Integer> pids = new LinkedHashSet<>(jdbc.queryForList("""
                    select pid
                    from pg_stat_activity
                    where datname = current_database()
                      and pid <> pg_backend_pid()
                      and pid <> ?
                      and state = 'active'
                      and wait_event_type = 'Lock'
                      and query ilike '%posts%'
                    """, Integer.class, gateBackendPid));
            if (pids.size() >= 2) {
                return new LockObservation(gateBackendPid, Set.copyOf(pids));
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        throw new AssertionError("Timed out waiting for two PostgreSQL lock waiters");
    }

    private static void assertTargetDatabase(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("select current_database()")) {
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo(TARGET_DATABASE);
            }
        }
    }

    private static int backendPid(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("select pg_backend_pid()")) {
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private static void lockPost(Connection connection, Long postId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id from posts where id = ? for update"
        )) {
            statement.setLong(1, postId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
            }
        }
    }

    private UserFixture insertUser(String tag) {
        String marker = UUID.randomUUID().toString();
        String userId = "t11" + marker.replace("-", "");
        String email = "t11-" + tag + "-" + marker + "@example.test";
        Long id = jdbc.queryForObject("""
                insert into tbl_user (
                    user_id, name, email, password, is_active,
                    is_account_verified, reset_otp_expire_at
                ) values (?, ?, ?, ?, true, true, 0)
                returning id
                """, Long.class, userId, "T11 " + tag, email, "t11-integration-only");
        assertThat(id).isNotNull();
        userIds.add(id);
        return new UserFixture(id, email, UserEntity.builder()
                .id(id)
                .userId(userId)
                .name("T11 " + tag)
                .email(email)
                .password("t11-integration-only")
                .isActive(true)
                .isAccountVerified(true)
                .resetOtpExpireAt(0L)
                .build());
    }

    private PostFixture insertPost(Long authorId, PostStatus targetStatus, String excerpt, String tag) {
        Instant createdAt = Instant.now().minusSeconds(90).truncatedTo(ChronoUnit.MICROS);
        Long id = inNewTransaction(() -> {
            PostEntity post = PostEntity.createDraft(
                    authorId,
                    "T11 " + tag + " " + UUID.randomUUID(),
                    "t11-" + tag + "-" + UUID.randomUUID(),
                    excerpt,
                    Map.of("type", "doc"),
                    PostVisibility.LAB,
                    null,
                    createdAt
            );
            if (targetStatus == PostStatus.PENDING_REVIEW || targetStatus == PostStatus.APPROVED) {
                post.submitForReview(createdAt.plusSeconds(30));
            }
            if (targetStatus == PostStatus.APPROVED) {
                post.applyReviewDecision(ReviewDecision.APPROVED, createdAt.plusSeconds(60));
            }
            if (post.getStatus() != targetStatus) {
                throw new IllegalArgumentException("Unsupported fixture status: " + targetStatus);
            }
            return postRepository.saveAndFlush(post).getId();
        });
        assertThat(id).isNotNull();
        postIds.add(id);
        return new PostFixture(id);
    }

    private PostSnapshot readPost(Long postId) {
        return inNewTransaction(() -> jdbc.queryForObject("""
                select status, excerpt, published_at, updated_at, deleted_at
                from posts
                where id = ?
                """, (rs, rowNum) -> new PostSnapshot(
                PostStatus.valueOf(rs.getString("status")),
                rs.getString("excerpt"),
                rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant()
        ), postId));
    }

    private long reviewCount(Long postId) {
        return inNewTransaction(() -> jdbc.queryForObject(
                "select count(*) from post_reviews where post_id = ?",
                Long.class,
                postId
        ));
    }

    private PersistedReview readOnlyReview(Long postId) {
        return inNewTransaction(() -> jdbc.queryForObject("""
                select reviewer_user_id, decision, reason, created_at
                from post_reviews
                where post_id = ?
                """, (rs, rowNum) -> new PersistedReview(
                rs.getLong("reviewer_user_id"),
                ReviewDecision.valueOf(rs.getString("decision")),
                rs.getString("reason"),
                rs.getTimestamp("created_at").toInstant()
        ), postId));
    }

    private <T> T inNewTransaction(java.util.function.Supplier<T> work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> work.get());
    }

    private String currentDatabase() {
        return jdbc.queryForObject("select current_database()", String.class);
    }

    private DatabaseCounts databaseCounts() {
        return new DatabaseCounts(
                jdbc.queryForObject("select count(*) from tbl_user", Long.class),
                jdbc.queryForObject("select count(*) from posts", Long.class),
                jdbc.queryForObject("select count(*) from post_reviews", Long.class)
        );
    }

    private static boolean hasStatus(CommandOutcome outcome, HttpStatus expected) {
        return outcome.failure() instanceof ResponseStatusException response
                && response.getStatusCode().value() == expected.value();
    }

    private record UserFixture(Long id, String email, UserEntity entity) {
    }

    private record PostFixture(Long id) {
    }

    private record ReviewAttempt(
            UserFixture reviewer,
            ReviewDecision decision,
            String reason,
            PostStatus expectedStatus
    ) {
    }

    private record PersistedReview(
            Long reviewerUserId,
            ReviewDecision decision,
            String reason,
            Instant createdAt
    ) {
    }

    private record PostSnapshot(
            PostStatus status,
            String excerpt,
            Instant publishedAt,
            Instant updatedAt,
            Instant deletedAt
    ) {
    }

    private record DatabaseCounts(long users, long posts, long reviews) {
    }

    private record LockObservation(int gateBackendPid, Set<Integer> waitingBackendPids) {
    }

    private record RaceExecution(
            CommandOutcome first,
            CommandOutcome second,
            LockObservation lockObservation
    ) {
        private java.util.List<CommandOutcome> outcomes() {
            return java.util.List.of(first, second);
        }
    }

    private record CommandOutcome(Object value, Throwable failure) {
        private static CommandOutcome success(Object value) {
            return new CommandOutcome(value, null);
        }

        private static CommandOutcome failure(Throwable failure) {
            return new CommandOutcome(null, failure);
        }

        private boolean succeeded() {
            return failure == null;
        }
    }
}
