package com.smartlab.service.impl;

import com.smartlab.dto.request.PostCommentRequest;
import com.smartlab.dto.response.CursorPageResponse;
import com.smartlab.dto.response.PostCommentResponse;
import com.smartlab.dto.response.PostFeedResponse;
import com.smartlab.entity.PostCommentEntity;
import com.smartlab.entity.PostEntity;
import com.smartlab.entity.PostReactionEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostReactionType;
import com.smartlab.enums.PostVisibility;
import com.smartlab.repo.ContentCategoryRepository;
import com.smartlab.repo.PostCommentRepository;
import com.smartlab.repo.PostReactionRepository;
import com.smartlab.repo.PostRepository;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostSocialServiceImplTest {
    private static final String EMAIL = "viewer@example.test";
    private static final Long VIEWER_ID = 41L;
    private static final Instant NOW = Instant.parse("2026-08-11T04:00:00Z");

    @Mock UserRepository users;
    @Mock PostRepository posts;
    @Mock ProjectMemberRepository memberships;
    @Mock ContentCategoryRepository categories;
    @Mock PostReactionRepository reactions;
    @Mock PostCommentRepository comments;

    private PostSocialServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PostSocialServiceImpl(users, posts, memberships, categories, reactions, comments);
    }

    @Test
    void feedUsesPublishedCursorOrderAndBatchesSocialAggregatesWithoutPrivateAuthorFields() throws Exception {
        UserEntity viewer = user(VIEWER_ID, "viewer-id", "Viewer");
        UserEntity author = user(72L, "public-author", "Nguyễn An");
        PostEntity first = publishedPost(20L, 72L, "newest", PostVisibility.LAB, null, NOW);
        PostEntity second = publishedPost(19L, 72L, "older", PostVisibility.PUBLIC, null, NOW.minusSeconds(1));
        active(viewer);
        when(memberships.findActiveProjectIdsByUserId(VIEWER_ID)).thenReturn(List.of());
        when(posts.findActivePublishedFeedPage(eq(List.of(-1L)), any(Instant.class), eq(Long.MAX_VALUE), any(Pageable.class)))
                .thenReturn(List.of(first, second));
        when(users.findAllById(any())).thenReturn(List.of(author));
        when(reactions.findAllByPostIdInAndUserId(List.of(20L), VIEWER_ID)).thenReturn(List.of(
                PostReactionEntity.create(20L, VIEWER_ID, PostReactionType.LOVE, NOW)
        ));
        PostReactionRepository.ReactionCountView count = org.mockito.Mockito.mock(PostReactionRepository.ReactionCountView.class);
        when(count.getPostId()).thenReturn(20L);
        when(count.getReactionType()).thenReturn(PostReactionType.LOVE);
        when(count.getCount()).thenReturn(3L);
        when(reactions.countByPostIds(List.of(20L))).thenReturn(List.of(count));
        when(comments.countActiveByPostIds(List.of(20L))).thenReturn(List.of());

        CursorPageResponse<PostFeedResponse> page = service.getFeed(EMAIL, null, 1);

        assertThat(page.items()).hasSize(1);
        assertThat(page.nextCursor()).isNotBlank();
        PostFeedResponse item = page.items().getFirst();
        assertThat(item.getSlug()).isEqualTo("newest");
        assertThat(item.getContentJson()).containsEntry("body", "Body newest");
        assertThat(item.getAuthor().getUserId()).isEqualTo("public-author");
        assertThat(item.getAuthor().getName()).isEqualTo("Nguyễn An");
        assertThat(item.getViewerReaction()).isEqualTo(PostReactionType.LOVE);
        assertThat(item.getReactionCount()).isEqualTo(3);
        assertThat(item.getCommentCount()).isZero();
    }

    @Test
    void anonymousFeedUsesDedicatedPublicQueryAndOmitsViewerReaction() throws Exception {
        UserEntity author = user(72L, "public-author", "Nguyễn An");
        PostEntity post = publishedPost(20L, 72L, "public-post", PostVisibility.PUBLIC, null, NOW);
        when(posts.findActivePublicPublishedFeedPage(any(Instant.class), eq(Long.MAX_VALUE), any(Pageable.class)))
                .thenReturn(List.of(post));
        when(users.findAllById(any())).thenReturn(List.of(author));
        PostReactionRepository.ReactionCountView count = org.mockito.Mockito.mock(PostReactionRepository.ReactionCountView.class);
        when(count.getPostId()).thenReturn(20L);
        when(count.getReactionType()).thenReturn(PostReactionType.LIKE);
        when(count.getCount()).thenReturn(2L);
        when(reactions.countByPostIds(List.of(20L))).thenReturn(List.of(count));
        when(comments.countActiveByPostIds(List.of(20L))).thenReturn(List.of());

        CursorPageResponse<PostFeedResponse> page = service.getFeed(null, null, 15);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.getSlug()).isEqualTo("public-post");
            assertThat(item.getViewerReaction()).isNull();
            assertThat(item.getReactionCount()).isEqualTo(2);
            assertThat(item.getCommentCount()).isZero();
        });
        verify(posts).findActivePublicPublishedFeedPage(any(Instant.class), eq(Long.MAX_VALUE), any(Pageable.class));
        verify(memberships, never()).findActiveProjectIdsByUserId(any());
        verify(reactions, never()).findAllByPostIdInAndUserId(any(), any());
    }

    @Test
    void feedRejectsInvalidCursorAndOutOfRangeLimit() {
        active(user(VIEWER_ID, "viewer-id", "Viewer"));
        assertBadRequest(() -> service.getFeed(EMAIL, "not-a-cursor", 15));
        assertBadRequest(() -> service.getFeed(EMAIL, null, 51));
        verify(posts, never()).findActivePublishedFeedPage(any(), any(), any(), any());
    }

    @Test
    void reactionCreateChangeAndSameValueAreIdempotent() throws Exception {
        UserEntity viewer = readableViewer();
        PostReactionEntity existing = PostReactionEntity.create(20L, VIEWER_ID, PostReactionType.LIKE, NOW);
        when(reactions.findByPostIdAndUserId(20L, VIEWER_ID))
                .thenReturn(Optional.empty(), Optional.of(existing), Optional.of(existing));
        when(reactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reactions.countByPostIds(List.of(20L))).thenReturn(List.of());

        assertThat(service.setReaction(EMAIL, 20L, PostReactionType.LIKE).viewerReaction()).isEqualTo(PostReactionType.LIKE);
        assertThat(service.setReaction(EMAIL, 20L, PostReactionType.LOVE).viewerReaction()).isEqualTo(PostReactionType.LOVE);
        Instant changedAt = existing.getUpdatedAt();
        assertThat(service.setReaction(EMAIL, 20L, PostReactionType.LOVE).viewerReaction()).isEqualTo(PostReactionType.LOVE);
        assertThat(existing.getUpdatedAt()).isEqualTo(changedAt);
        verify(reactions, org.mockito.Mockito.times(3)).save(any(PostReactionEntity.class));
        assertThat(viewer.getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void removeReactionIsIdempotentWhenAbsent() throws Exception {
        readableViewer();
        when(reactions.findByPostIdAndUserId(20L, VIEWER_ID)).thenReturn(Optional.empty());
        when(reactions.countByPostIds(List.of(20L))).thenReturn(List.of());

        assertThat(service.removeReaction(EMAIL, 20L).viewerReaction()).isNull();

        verify(reactions, never()).delete(any());
        verify(reactions).flush();
    }

    @Test
    void interactionsConcealUnreadableProjectPost() throws Exception {
        UserEntity viewer = user(VIEWER_ID, "viewer-id", "Viewer");
        PostEntity projectPost = publishedPost(20L, 72L, "project", PostVisibility.PROJECT, 99L, NOW);
        active(viewer);
        when(posts.findActiveById(20L)).thenReturn(Optional.of(projectPost));
        when(memberships.findActiveProjectIdsByUserId(VIEWER_ID)).thenReturn(List.of());

        assertNotFound(() -> service.setReaction(EMAIL, 20L, PostReactionType.LIKE));
        assertNotFound(() -> service.createComment(EMAIL, 20L, new PostCommentRequest("Hello")));
        verify(reactions, never()).save(any());
        verify(comments, never()).save(any());
    }

    @Test
    void createCommentTrimsContentAndReturnsOnlyPublicAuthorIdentity() throws Exception {
        UserEntity viewer = readableViewer();
        when(comments.save(any())).thenAnswer(invocation -> {
            PostCommentEntity comment = invocation.getArgument(0);
            set(comment, "id", 301L);
            return comment;
        });

        PostCommentResponse response = service.createComment(EMAIL, 20L, new PostCommentRequest("  Nội dung  "));

        assertThat(response.id()).isEqualTo(301L);
        assertThat(response.content()).isEqualTo("Nội dung");
        assertThat(response.author().getUserId()).isEqualTo("viewer-id");
        assertThat(response.author().getName()).isEqualTo("Viewer");
        assertThat(viewer.getPassword()).isNull();
    }

    @Test
    void commentAuthorCanEditAndSoftDeleteButOtherUserCannot() throws Exception {
        readableViewer();
        PostCommentEntity own = PostCommentEntity.create(20L, VIEWER_ID, "Old", NOW);
        set(own, "id", 301L);
        when(comments.findActiveByIdAndPostId(301L, 20L)).thenReturn(Optional.of(own));

        assertThat(service.editComment(EMAIL, 20L, 301L, new PostCommentRequest("New")).content()).isEqualTo("New");
        service.deleteComment(EMAIL, 20L, 301L);
        assertThat(own.getDeletedAt()).isNotNull();

        PostCommentEntity other = PostCommentEntity.create(20L, 999L, "Other", NOW);
        set(other, "id", 302L);
        when(comments.findActiveByIdAndPostId(302L, 20L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.editComment(EMAIL, 20L, 302L, new PostCommentRequest("No")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void commentListUsesDeterministicNewestFirstCursorAndBatchesAuthors() throws Exception {
        readableViewer();
        PostCommentEntity newest = PostCommentEntity.create(20L, 72L, "Newest", NOW);
        set(newest, "id", 10L);
        PostCommentEntity older = PostCommentEntity.create(20L, 72L, "Older", NOW.minusSeconds(1));
        set(older, "id", 9L);
        when(comments.findActivePage(eq(20L), any(Instant.class), eq(Long.MAX_VALUE), any(Pageable.class)))
                .thenReturn(List.of(newest, older));
        when(users.findAllById(any())).thenReturn(List.of(user(72L, "author-id", "Author")));

        CursorPageResponse<PostCommentResponse> page = service.getComments(EMAIL, 20L, null, 1);

        assertThat(page.items()).extracting(PostCommentResponse::content).containsExactly("Newest");
        assertThat(page.nextCursor()).isNotBlank();
    }

    private UserEntity readableViewer() throws Exception {
        UserEntity viewer = user(VIEWER_ID, "viewer-id", "Viewer");
        active(viewer);
        when(posts.findActiveById(20L)).thenReturn(Optional.of(
                publishedPost(20L, 72L, "published", PostVisibility.LAB, null, NOW)
        ));
        when(memberships.findActiveProjectIdsByUserId(VIEWER_ID)).thenReturn(List.of());
        return viewer;
    }

    private void active(UserEntity viewer) {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(viewer));
    }

    private static UserEntity user(Long id, String userId, String name) {
        return UserEntity.builder().id(id).userId(userId).name(name).email(EMAIL).isActive(true).build();
    }

    private static PostEntity publishedPost(
            Long id, Long authorId, String slug, PostVisibility visibility, Long projectId, Instant publishedAt
    ) throws Exception {
        PostEntity post = PostEntity.createDraft(authorId, "Title " + slug, slug, "Excerpt",
                Map.of("body", "Body " + slug), visibility, null, publishedAt.minusSeconds(20));
        post.publishDirect(publishedAt);
        set(post, "id", id);
        set(post, "projectId", projectId);
        return post;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void assertBadRequest(ThrowingCall call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static void assertNotFound(ThrowingCall call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @FunctionalInterface
    private interface ThrowingCall { void run() throws Exception; }
}
