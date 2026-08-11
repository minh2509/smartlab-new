package com.smartlab.service.impl;

import com.smartlab.dto.request.PostCommentRequest;
import com.smartlab.dto.request.ReviewPostRequest;
import com.smartlab.dto.response.PostFeedResponse;
import com.smartlab.enums.PostReactionType;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.ReviewDecision;
import com.smartlab.service.PostService;
import com.smartlab.service.PostSocialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class PostSocialPostgresIntegrationTest {
    private static final String TARGET_DATABASE = "smartlab_rich_editor_it";

    @Autowired JdbcTemplate jdbc;
    @Autowired PostSocialService socialService;
    @Autowired PostService postService;

    @BeforeEach
    void requiresDedicatedDatabase() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo(TARGET_DATABASE);
    }

    @Test
    void feedMineProjectVisibilityAndCursorPaginationAreSeparated() {
        UserFixture viewer = insertUser("viewer");
        UserFixture author = insertUser("author");
        long projectId = insertProject(author.id());
        insertMembership(projectId, viewer.id());
        Instant publishedAt = Instant.parse("2026-08-11T06:00:00Z");
        long lowerId = insertPost(author.id(), null, "public-low", "PUBLIC", "PUBLISHED", publishedAt);
        long higherId = insertPost(author.id(), projectId, "project-high", "PROJECT", "PUBLISHED", publishedAt);
        insertPost(viewer.id(), null, "own-draft", "LAB", "DRAFT", null);

        var first = socialService.getFeed(viewer.email(), null, 1);
        assertThat(first.items()).extracting(PostFeedResponse::getId).containsExactly(higherId);
        assertThat(first.nextCursor()).isNotBlank();
        var second = socialService.getFeed(viewer.email(), first.nextCursor(), 1);
        assertThat(second.items()).extracting(PostFeedResponse::getId).containsExactly(lowerId);
        assertThat(first.items()).allSatisfy(item -> assertThat(item.getSlug()).doesNotContain("own-draft"));
        assertThat(postService.getMyPosts(viewer.email())).singleElement()
                .satisfies(item -> assertThat(item.getSlug()).startsWith("own-draft-"));

        jdbc.update("update project_members set status = 'REMOVED', removed_at = now() where project_id = ? and user_id = ?",
                projectId, viewer.id());
        assertThat(socialService.getFeed(viewer.email(), null, 50).items().stream()
                .filter(item -> item.getSlug().startsWith("public-low-") || item.getSlug().startsWith("project-high-"))
                .map(PostFeedResponse::getSlug).toList())
                .singleElement().satisfies(slug -> assertThat(slug).startsWith("public-low-"));
    }

    @Test
    void anonymousFeedContainsOnlyPublicPublishedPostsWithDeterministicCursorAndAggregates() {
        UserFixture author = insertUser("anonymous-feed-author");
        UserFixture reactor = insertUser("anonymous-feed-reactor");
        long projectId = insertProject(author.id());
        Instant publishedAt = Instant.parse("2026-08-11T06:30:00Z");
        long lowerPublicId = insertPost(author.id(), null, "anonymous-public-low", "PUBLIC", "PUBLISHED", publishedAt);
        insertPost(author.id(), null, "anonymous-lab", "LAB", "PUBLISHED", publishedAt.plusSeconds(5));
        insertPost(author.id(), projectId, "anonymous-project", "PROJECT", "PUBLISHED", publishedAt.plusSeconds(5));
        insertPost(author.id(), null, "anonymous-draft", "PUBLIC", "DRAFT", null);
        insertPost(author.id(), null, "anonymous-pending", "PUBLIC", "PENDING_REVIEW", null);
        insertPost(author.id(), null, "anonymous-revision", "PUBLIC", "REVISION_REQUIRED", null);
        insertPost(author.id(), null, "anonymous-approved", "PUBLIC", "APPROVED", null);
        insertPost(author.id(), null, "anonymous-rejected", "PUBLIC", "REJECTED", null);
        long higherPublicId = insertPost(author.id(), null, "anonymous-public-high", "PUBLIC", "PUBLISHED", publishedAt);
        jdbc.update("insert into post_reactions(post_id,user_id,reaction_type) values(?,?,'LIKE')",
                higherPublicId, reactor.id());
        jdbc.update("insert into post_comments(post_id,author_user_id,content) values(?,?,'Public aggregate')",
                higherPublicId, reactor.id());

        var first = socialService.getFeed(null, null, 1);
        var second = socialService.getFeed(null, first.nextCursor(), 1);

        assertThat(first.items()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(higherPublicId);
            assertThat(item.getVisibility().name()).isEqualTo("PUBLIC");
            assertThat(item.getViewerReaction()).isNull();
            assertThat(item.getReactionCount()).isEqualTo(1);
            assertThat(item.getCommentCount()).isEqualTo(1);
            assertThat(item.getAuthor().getUserId()).startsWith("social-");
            assertThat(item.getAuthor().getName()).isEqualTo("Social anonymous-feed-author");
        });
        assertThat(first.nextCursor()).isNotBlank();
        assertThat(second.items()).extracting(PostFeedResponse::getId).containsExactly(lowerPublicId);
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    void anonymousPermalinkConcealsNonPublicUnpublishedAndDeletedPosts() {
        UserFixture author = insertUser("anonymous-detail-author");
        long projectId = insertProject(author.id());
        Instant publishedAt = Instant.parse("2026-08-11T07:00:00Z");
        long publicId = insertPost(author.id(), null, "detail-public", "PUBLIC", "PUBLISHED", publishedAt);
        long labId = insertPost(author.id(), null, "detail-lab", "LAB", "PUBLISHED", publishedAt);
        long projectPostId = insertPost(author.id(), projectId, "detail-project", "PROJECT", "PUBLISHED", publishedAt);
        long deletedId = insertPost(author.id(), null, "detail-deleted", "PUBLIC", "PUBLISHED", publishedAt);
        jdbc.update("update posts set deleted_at = now() where id = ?", deletedId);
        long[] unpublishedIds = {
                insertPost(author.id(), null, "detail-draft", "PUBLIC", "DRAFT", null),
                insertPost(author.id(), null, "detail-pending", "PUBLIC", "PENDING_REVIEW", null),
                insertPost(author.id(), null, "detail-revision", "PUBLIC", "REVISION_REQUIRED", null),
                insertPost(author.id(), null, "detail-approved", "PUBLIC", "APPROVED", null),
                insertPost(author.id(), null, "detail-rejected", "PUBLIC", "REJECTED", null)
        };

        String publicSlug = slugFor(publicId);
        assertThat(postService.getPostBySlug(null, publicSlug).getId()).isEqualTo(publicId);
        assertNotFound(() -> postService.getPostBySlug(null, slugFor(labId)));
        assertNotFound(() -> postService.getPostBySlug(null, slugFor(projectPostId)));
        assertNotFound(() -> postService.getPostBySlug(null, slugFor(deletedId)));
        for (long unpublishedId : unpublishedIds) {
            assertNotFound(() -> postService.getPostBySlug(null, slugFor(unpublishedId)));
        }
    }

    @Test
    void reactionCreateChangeSameValueRemoveAndAggregatesUseOneRowPerUserPost() {
        UserFixture viewer = insertUser("viewer");
        UserFixture other = insertUser("other");
        long postId = insertPost(other.id(), null, "reaction-post", "LAB", "PUBLISHED", Instant.now());

        assertThat(socialService.setReaction(viewer.email(), postId, PostReactionType.LIKE).reactionCount()).isEqualTo(1);
        assertThat(socialService.setReaction(viewer.email(), postId, PostReactionType.LOVE).viewerReaction())
                .isEqualTo(PostReactionType.LOVE);
        socialService.setReaction(viewer.email(), postId, PostReactionType.LOVE);
        assertThat(count("select count(*) from post_reactions where post_id = ? and user_id = ?", postId, viewer.id()))
                .isEqualTo(1);

        PostFeedResponse feed = socialService.getFeed(viewer.email(), null, 10).items().getFirst();
        assertThat(feed.getViewerReaction()).isEqualTo(PostReactionType.LOVE);
        assertThat(feed.getReactionCounts().get(PostReactionType.LOVE)).isEqualTo(1);
        assertThat(feed.getReactionCount()).isEqualTo(1);

        assertThat(socialService.removeReaction(viewer.email(), postId).reactionCount()).isZero();
        assertThat(socialService.removeReaction(viewer.email(), postId).viewerReaction()).isNull();
    }

    @Test
    void commentsValidateOwnershipSoftDeleteAndUpdateFeedCount() {
        UserFixture author = insertUser("author");
        UserFixture other = insertUser("other");
        long postId = insertPost(other.id(), null, "comment-post", "PUBLIC", "PUBLISHED", Instant.now());

        var created = socialService.createComment(author.email(), postId, new PostCommentRequest("  Nội dung  "));
        assertThat(created.content()).isEqualTo("Nội dung");
        assertThat(socialService.getComments(author.email(), postId, null, 20).items()).hasSize(1);
        assertThat(socialService.getFeed(author.email(), null, 10).items().getFirst().getCommentCount()).isEqualTo(1);
        assertThat(socialService.editComment(author.email(), postId, created.id(), new PostCommentRequest("Đã sửa")).content())
                .isEqualTo("Đã sửa");
        assertThatThrownBy(() -> socialService.editComment(other.email(), postId, created.id(), new PostCommentRequest("Không")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        socialService.deleteComment(author.email(), postId, created.id());
        assertThat(socialService.getComments(author.email(), postId, null, 20).items()).isEmpty();
        assertThat(socialService.getFeed(author.email(), null, 10).items().getFirst().getCommentCount()).isZero();
        assertThat(count("select count(*) from post_comments where id = ? and deleted_at is not null", created.id())).isEqualTo(1);
        assertThatThrownBy(() -> socialService.createComment(author.email(), postId, new PostCommentRequest("   ")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void approvedReviewImmediatelyPublishesIntoFeedWithProjectVisibility() {
        UserFixture reviewer = insertUser("reviewer");
        UserFixture author = insertUser("publish-author");
        UserFixture member = insertUser("publish-member");
        UserFixture outsider = insertUser("publish-outsider");
        long projectId = insertProject(author.id());
        insertMembership(projectId, member.id());

        long postId = insertPost(author.id(), projectId, "approved-project", "PROJECT", "PENDING_REVIEW", null);
        String slug = jdbc.queryForObject("select slug from posts where id = ?", String.class, postId);
        assertThat(postService.getReviewablePosts(reviewer.email()))
                .extracting(item -> item.getId()).contains(postId);

        var published = postService.reviewPost(
                reviewer.email(),
                postId,
                new ReviewPostRequest(ReviewDecision.APPROVED, "ready")
        );

        assertThat(published.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(published.getPublishedAt()).isNotNull().isEqualTo(published.getUpdatedAt());
        assertThat(jdbc.queryForObject("select decision from post_reviews where post_id = ?", String.class, postId))
                .isEqualTo("APPROVED");
        assertThat(postService.getReviewablePosts(reviewer.email()))
                .extracting(item -> item.getId()).doesNotContain(postId);
        assertThatThrownBy(() -> postService.reviewPost(
                reviewer.email(),
                postId,
                new ReviewPostRequest(ReviewDecision.APPROVED, "again")
        )).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(postService.getPostBySlug(member.email(), slug).getId()).isEqualTo(postId);
        assertThat(socialService.getFeed(member.email(), null, 50).items())
                .extracting(PostFeedResponse::getId).contains(postId);
        assertNotFound(() -> postService.getPostBySlug(outsider.email(), slug));
        assertThat(socialService.getFeed(outsider.email(), null, 50).items())
                .extracting(PostFeedResponse::getId).doesNotContain(postId);
    }

    private UserFixture insertUser(String label) {
        String key = UUID.randomUUID().toString().replace("-", "");
        String email = "social-" + label + "-" + key + "@test";
        Long id = jdbc.queryForObject("""
                insert into tbl_user(user_id,name,email,password,is_active,is_account_verified,reset_otp_expire_at)
                values(?,?,?,'',true,true,0) returning id
                """, Long.class, "social-" + key, "Social " + label, email);
        return new UserFixture(id, email);
    }

    private long insertProject(long creatorId) {
        return jdbc.queryForObject("""
                insert into projects(code,name,project_type,status,is_public,is_featured,created_by_user_id,created_at,updated_at)
                values(?, 'Social project', 'RESEARCH', 'IN_PROGRESS', false, false, ?, now(), now()) returning id
                """, Long.class, "SOCIAL-" + UUID.randomUUID().toString().substring(0, 8), creatorId);
    }

    private void insertMembership(long projectId, long userId) {
        jdbc.update("""
                insert into project_members(project_id,user_id,project_role,status,joined_at)
                values(?,?,'MEMBER','ACTIVE',now())
                """, projectId, userId);
    }

    private long insertPost(long authorId, Long projectId, String slugPrefix, String visibility, String status, Instant publishedAt) {
        String slug = slugPrefix + "-" + UUID.randomUUID();
        return jdbc.queryForObject("""
                insert into posts(author_user_id,project_id,title,slug,excerpt,content_json,visibility,status,published_at,created_at,updated_at)
                values(?,?,?,?,'Excerpt',jsonb_build_object('body', ?),?,?,?,now(),now()) returning id
                """, Long.class, authorId, projectId, "Title " + slugPrefix, slug, "Body " + slugPrefix,
                visibility, status, publishedAt == null ? null : Timestamp.from(publishedAt));
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private String slugFor(long postId) {
        return jdbc.queryForObject("select slug from posts where id = ?", String.class, postId);
    }

    private static void assertNotFound(Runnable work) {
        assertThatThrownBy(work::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private record UserFixture(Long id, String email) {}
}
