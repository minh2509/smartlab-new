package com.smartlab.service.impl;

import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.entity.PostEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
class PostDirectPublishPostgresIntegrationTest {

    private static final String TARGET_DATABASE = "smartlab_rich_editor_it";

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
    void requireExactCompatibleIntegrationDatabase() {
        assertThat(currentDatabase()).isEqualTo(TARGET_DATABASE);
        assertThat(jdbc.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('tbl_user', 'posts', 'post_reviews')
                """, Long.class)).isEqualTo(3L);
        String postStatusCheck = jdbc.queryForObject("""
                select pg_get_constraintdef(oid)
                from pg_constraint
                where conrelid = 'public.posts'::regclass
                  and contype = 'c'
                  and pg_get_constraintdef(oid) ilike '%status%'
                """, String.class);
        assertThat(postStatusCheck).contains(
                "DRAFT", "PENDING_REVIEW", "REVISION_REQUIRED", "APPROVED", "REJECTED", "PUBLISHED"
        );
        String reviewDecisionCheck = jdbc.queryForObject("""
                select pg_get_constraintdef(oid)
                from pg_constraint
                where conrelid = 'public.post_reviews'::regclass
                  and contype = 'c'
                  and pg_get_constraintdef(oid) ilike '%revision_required%'
                """, String.class);
        assertThat(reviewDecisionCheck).contains("APPROVED", "REVISION_REQUIRED", "REJECTED");
        baselineCounts = databaseCounts();
    }

    @AfterEach
    void cleanExactFixturesAndRestoreBaseline() {
        assertThat(currentDatabase()).isEqualTo(TARGET_DATABASE);
        postIds.forEach(id -> jdbc.update("delete from posts where id = ?", id));
        userIds.forEach(id -> jdbc.update("delete from tbl_user where id = ?", id));
        postIds.clear();
        userIds.clear();
        assertThat(databaseCounts()).isEqualTo(baselineCounts);
    }

    @Test
    void ownActiveDraftDirectPublishCommitsPublicationWithoutReviewHistory() {
        UserFixture owner = insertUser("commit-owner");
        PostFixture post = insertPost(owner.id(), PostStatus.DRAFT, "commit");
        when(userRepository.findByEmail(owner.email())).thenReturn(Optional.of(owner.entity()));

        PostDetailResponse response = postService.directPublishPost(owner.email(), post.id());

        PostSnapshot persisted = readPost(post.id());
        assertThat(response.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(persisted.status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(persisted.publishedAt()).isNotNull().isEqualTo(persisted.updatedAt());
        assertThat(response.getPublishedAt()).isEqualTo(persisted.publishedAt());
        assertThat(response.getUpdatedAt()).isEqualTo(persisted.updatedAt());
        assertThat(reviewCount(post.id())).isZero();
    }

    @Test
    void repeatedDirectPublishReturnsConflictWithoutRewritingPublicationTimestamps() {
        UserFixture owner = insertUser("repeat-owner");
        PostFixture post = insertPost(owner.id(), PostStatus.DRAFT, "repeat");
        when(userRepository.findByEmail(owner.email())).thenReturn(Optional.of(owner.entity()));

        postService.directPublishPost(owner.email(), post.id());
        PostSnapshot firstPublication = readPost(post.id());

        assertStatus(HttpStatus.CONFLICT, () -> postService.directPublishPost(owner.email(), post.id()));

        PostSnapshot persisted = readPost(post.id());
        assertThat(persisted.status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(persisted.publishedAt()).isEqualTo(firstPublication.publishedAt());
        assertThat(persisted.updatedAt()).isEqualTo(firstPublication.updatedAt());
        assertThat(reviewCount(post.id())).isZero();
    }

    @Test
    void nonOwnerDraftReturnsForbiddenAndPersistsNoMutation() {
        UserFixture caller = insertUser("nonowner-caller");
        UserFixture owner = insertUser("nonowner-owner");
        PostFixture post = insertPost(owner.id(), PostStatus.DRAFT, "nonowner-draft");
        when(userRepository.findByEmail(caller.email())).thenReturn(Optional.of(caller.entity()));
        PostSnapshot before = readPost(post.id());

        assertStatus(HttpStatus.FORBIDDEN, () -> postService.directPublishPost(caller.email(), post.id()));

        assertThat(readPost(post.id())).isEqualTo(before);
        assertThat(reviewCount(post.id())).isZero();
    }

    @Test
    void nonOwnerApprovedReturnsForbiddenBeforeLifecycleConflictAndPersistsNoMutation() {
        UserFixture caller = insertUser("disclosure-caller");
        UserFixture owner = insertUser("disclosure-owner");
        PostFixture post = insertPost(owner.id(), PostStatus.APPROVED, "nonowner-approved");
        when(userRepository.findByEmail(caller.email())).thenReturn(Optional.of(caller.entity()));
        PostSnapshot before = readPost(post.id());

        assertStatus(HttpStatus.FORBIDDEN, () -> postService.directPublishPost(caller.email(), post.id()));

        assertThat(readPost(post.id())).isEqualTo(before);
        assertThat(reviewCount(post.id())).isZero();
    }

    @Test
    void authorlessDraftReturnsForbiddenAndPersistsNoMutation() {
        UserFixture caller = insertUser("authorless-caller");
        PostFixture post = insertPost(null, PostStatus.DRAFT, "authorless");
        when(userRepository.findByEmail(caller.email())).thenReturn(Optional.of(caller.entity()));
        PostSnapshot before = readPost(post.id());

        assertStatus(HttpStatus.FORBIDDEN, () -> postService.directPublishPost(caller.email(), post.id()));

        assertThat(readPost(post.id())).isEqualTo(before);
        assertThat(reviewCount(post.id())).isZero();
    }

    @Test
    void ownApprovedReturnsConflictAndDoesNotInvokeNormalPublication() {
        UserFixture owner = insertUser("approved-owner");
        PostFixture post = insertPost(owner.id(), PostStatus.APPROVED, "approved-owner");
        when(userRepository.findByEmail(owner.email())).thenReturn(Optional.of(owner.entity()));
        PostSnapshot before = readPost(post.id());

        assertStatus(HttpStatus.CONFLICT, () -> postService.directPublishPost(owner.email(), post.id()));

        assertThat(readPost(post.id())).isEqualTo(before);
        assertThat(reviewCount(post.id())).isZero();
    }

    @Test
    void softDeletedOwnedDraftReturnsNotFoundAndPersistsNoPublication() {
        UserFixture owner = insertUser("deleted-owner");
        PostFixture post = insertPost(owner.id(), PostStatus.DRAFT, "deleted");
        when(userRepository.findByEmail(owner.email())).thenReturn(Optional.of(owner.entity()));
        Instant deletedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        assertThat(jdbc.update("update posts set deleted_at = ? where id = ?", Timestamp.from(deletedAt), post.id()))
                .isEqualTo(1);
        PostSnapshot before = readPost(post.id());

        assertStatus(HttpStatus.NOT_FOUND, () -> postService.directPublishPost(owner.email(), post.id()));

        assertThat(readPost(post.id())).isEqualTo(before);
        assertThat(reviewCount(post.id())).isZero();
    }

    private UserFixture insertUser(String tag) {
        String marker = UUID.randomUUID().toString();
        String userId = "a4" + marker.replace("-", "");
        String email = "t11a4-" + tag + "-" + marker + "@example.test";
        Long id = jdbc.queryForObject("""
                insert into tbl_user (
                    user_id, name, email, password, is_active,
                    is_account_verified, reset_otp_expire_at
                ) values (?, ?, ?, ?, true, true, NULL)
                returning id
                """, Long.class, userId, "T11A4 " + tag, email, "t11a4-integration-only");
        assertThat(id).isNotNull();
        userIds.add(id);
        return new UserFixture(id, email, UserEntity.builder()
                .id(id)
                .userId(userId)
                .name("T11A4 " + tag)
                .email(email)
                .password("t11a4-integration-only")
                .isActive(true)
                .isAccountVerified(true)
                .resetOtpExpireAt(0L)
                .build());
    }

    private PostFixture insertPost(Long authorId, PostStatus targetStatus, String tag) {
        Instant createdAt = Instant.now().minusSeconds(90).truncatedTo(ChronoUnit.MICROS);
        Long id = inNewTransaction(() -> {
            PostEntity post = PostEntity.createDraft(
                    authorId,
                    "T11A4 " + tag + " " + UUID.randomUUID(),
                    "t11a4-" + tag + "-" + UUID.randomUUID(),
                    "T11A4 PostgreSQL direct-publish acceptance",
                    Map.of("type", "doc"),
                    PostVisibility.LAB,
                    null,
                    createdAt
            );
            if (targetStatus == PostStatus.APPROVED) {
                post.submitForReview(createdAt.plusSeconds(30));
            }
            if (post.getStatus() != targetStatus && targetStatus != PostStatus.APPROVED) {
                throw new IllegalArgumentException("Unsupported fixture status: " + targetStatus);
            }
            return postRepository.saveAndFlush(post).getId();
        });
        if (targetStatus == PostStatus.APPROVED) {
            jdbc.update("update posts set status = 'APPROVED' where id = ?", id);
        }
        assertThat(id).isNotNull();
        postIds.add(id);
        return new PostFixture(id);
    }

    private PostSnapshot readPost(Long postId) {
        return inNewTransaction(() -> jdbc.queryForObject("""
                select author_user_id, status, published_at, updated_at, deleted_at
                from posts
                where id = ?
                """, (rs, rowNum) -> new PostSnapshot(
                rs.getObject("author_user_id", Long.class),
                PostStatus.valueOf(rs.getString("status")),
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

    private DatabaseCounts databaseCounts() {
        return new DatabaseCounts(
                jdbc.queryForObject("select count(*) from tbl_user", Long.class),
                jdbc.queryForObject("select count(*) from posts", Long.class),
                jdbc.queryForObject("select count(*) from post_reviews", Long.class)
        );
    }

    private <T> T inNewTransaction(java.util.function.Supplier<T> work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> work.get());
    }

    private String currentDatabase() {
        return jdbc.queryForObject("select current_database()", String.class);
    }

    private static void assertStatus(HttpStatus expected, ThrowingCommand command) {
        assertThatThrownBy(command::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(failure -> assertThat(((ResponseStatusException) failure).getStatusCode().value())
                        .isEqualTo(expected.value()));
    }

    @FunctionalInterface
    private interface ThrowingCommand {
        void run();
    }

    private record UserFixture(Long id, String email, UserEntity entity) {
    }

    private record PostFixture(Long id) {
    }

    private record PostSnapshot(
            Long authorUserId,
            PostStatus status,
            Instant publishedAt,
            Instant updatedAt,
            Instant deletedAt
    ) {
    }

    private record DatabaseCounts(long users, long posts, long reviews) {
    }
}
