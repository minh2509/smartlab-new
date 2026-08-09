package com.smartlab.service.impl;

import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.dto.response.PostSummaryResponse;
import com.smartlab.entity.ContentCategoryEntity;
import com.smartlab.entity.PostEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import com.smartlab.repo.ContentCategoryRepository;
import com.smartlab.repo.PostRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.PostSlugGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostReadServiceImplTest {

    private static final String VIEWER_EMAIL = "viewer@example.edu";
    private static final Long VIEWER_ID = 41L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private ContentCategoryRepository contentCategoryRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private PostSlugGenerator postSlugGenerator;
    @Mock
    private PostCreateAttemptService postCreateAttemptService;

    private PostServiceImpl postService;

    @BeforeEach
    void setUp() {
        postService = new PostServiceImpl(
                userRepository,
                contentCategoryRepository,
                postRepository,
                postSlugGenerator,
                postCreateAttemptService
        );
    }

    @Test
    void listResolvesActiveViewerUsesReadableQueryAndPreservesRepositoryOrder() {
        PostEntity ownDraft = post(5L, VIEWER_ID, PostStatus.DRAFT, PostVisibility.LAB, null, "own-draft", 5);
        PostEntity ownProject = post(4L, VIEWER_ID, PostStatus.APPROVED, PostVisibility.PROJECT, null, "own-project", 4);
        PostEntity publishedPublic = post(3L, 99L, PostStatus.PUBLISHED, PostVisibility.PUBLIC, null, "public", 3);
        PostEntity publishedLab = post(2L, 98L, PostStatus.PUBLISHED, PostVisibility.LAB, null, "lab", 2);
        activeViewer();
        when(postRepository.findActiveReadableByViewerUserId(VIEWER_ID))
                .thenReturn(List.of(ownDraft, ownProject, publishedPublic, publishedLab));

        List<PostSummaryResponse> responses = postService.getReadablePosts(VIEWER_EMAIL);

        assertThat(responses).extracting(PostSummaryResponse::getId).containsExactly(5L, 4L, 3L, 2L);
        verify(postRepository).findActiveReadableByViewerUserId(VIEWER_ID);
        verify(postRepository, never()).save(any(PostEntity.class));
        verifyNoInteractions(contentCategoryRepository, postSlugGenerator);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listBatchMapsCategoriesIncludingInactiveRowsAndLeavesMissingRowsNull() {
        PostEntity first = post(5L, VIEWER_ID, PostStatus.DRAFT, PostVisibility.LAB, 7L, "first", 5);
        PostEntity second = post(4L, VIEWER_ID, PostStatus.DRAFT, PostVisibility.LAB, 7L, "second", 4);
        PostEntity missing = post(3L, VIEWER_ID, PostStatus.DRAFT, PostVisibility.LAB, 8L, "missing", 3);
        ContentCategoryEntity inactiveNews = category(7L, "NEWS", "News", false);
        activeViewer();
        when(postRepository.findActiveReadableByViewerUserId(VIEWER_ID)).thenReturn(List.of(first, second, missing));
        when(contentCategoryRepository.findAllById(any(Iterable.class))).thenReturn(List.of(inactiveNews));

        List<PostSummaryResponse> responses = postService.getReadablePosts(VIEWER_EMAIL);

        assertThat(responses.get(0).getCategory()).extracting("id", "code", "name")
                .containsExactly(7L, "NEWS", "News");
        assertThat(responses.get(1).getCategory()).extracting("id", "code", "name")
                .containsExactly(7L, "NEWS", "News");
        assertThat(responses.get(2).getCategory()).isNull();
        ArgumentCaptor<Iterable<Long>> ids = ArgumentCaptor.forClass(Iterable.class);
        verify(contentCategoryRepository).findAllById(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(7L, 8L);
        verify(contentCategoryRepository, never()).findById(any());
        verify(contentCategoryRepository, never()).findByIdAndIsActiveTrue(any());
    }

    @Test
    void rejectsMissingViewerForListWithoutReadingOrMutatingPosts() {
        when(userRepository.findByEmail(VIEWER_EMAIL)).thenReturn(Optional.empty());

        assertUnauthorized(() -> postService.getReadablePosts(VIEWER_EMAIL));
        verifyNoInteractions(postRepository, contentCategoryRepository, postSlugGenerator);
    }

    @Test
    void rejectsInactiveViewerForListWithoutReadingOrMutatingPosts() {
        when(userRepository.findByEmail(VIEWER_EMAIL)).thenReturn(Optional.of(user(VIEWER_ID, false)));

        assertUnauthorized(() -> postService.getReadablePosts(VIEWER_EMAIL));
        verifyNoInteractions(postRepository, contentCategoryRepository, postSlugGenerator);
    }

    @Test
    void rejectsMissingViewerForDetailWithoutReadingOrMutatingPosts() {
        when(userRepository.findByEmail(VIEWER_EMAIL)).thenReturn(Optional.empty());

        assertUnauthorized(() -> postService.getPostBySlug(VIEWER_EMAIL, "post"));
        verifyNoInteractions(postRepository, contentCategoryRepository, postSlugGenerator);
    }

    @Test
    void rejectsInactiveViewerForDetailWithoutReadingOrMutatingPosts() {
        when(userRepository.findByEmail(VIEWER_EMAIL)).thenReturn(Optional.of(user(VIEWER_ID, false)));

        assertUnauthorized(() -> postService.getPostBySlug(VIEWER_EMAIL, "post"));
        verifyNoInteractions(postRepository, contentCategoryRepository, postSlugGenerator);
    }

    @ParameterizedTest
    @MethodSource("readableByOwnerOrPublishedVisibility")
    void detailAllowsOwnerAndPublishedPublicOrLabPosts(Long authorId, PostStatus status, PostVisibility visibility) {
        PostEntity post = post(1L, authorId, status, visibility, null, "readable", 1);
        activeViewer();
        when(postRepository.findActiveBySlug("readable")).thenReturn(Optional.of(post));

        PostDetailResponse response = postService.getPostBySlug(VIEWER_EMAIL, "readable");

        assertThat(response.getSlug()).isEqualTo("readable");
        verifyNoInteractions(contentCategoryRepository);
    }

    @ParameterizedTest
    @MethodSource("unreadablePosts")
    void detailHidesNonReadableExistingPostAsNotFound(Long authorId, PostStatus status, PostVisibility visibility) {
        PostEntity post = post(1L, authorId, status, visibility, null, "hidden", 1);
        activeViewer();
        when(postRepository.findActiveBySlug("hidden")).thenReturn(Optional.of(post));

        assertNotFound(() -> postService.getPostBySlug(VIEWER_EMAIL, "hidden"));
        verifyNoInteractions(contentCategoryRepository);
    }

    @Test
    void detailReturnsNotFoundForMissingOrSoftDeletedSlugFromActiveLookup() {
        activeViewer();
        when(postRepository.findActiveBySlug("missing")).thenReturn(Optional.empty());

        assertNotFound(() -> postService.getPostBySlug(VIEWER_EMAIL, "missing"));
        verifyNoInteractions(contentCategoryRepository);
    }

    @Test
    void detailMapsInactiveCategoryWithoutTreatingItAsUnavailable() {
        PostEntity post = post(1L, VIEWER_ID, PostStatus.DRAFT, PostVisibility.PROJECT, 7L, "categorized", 1);
        ContentCategoryEntity inactiveCategory = category(7L, "NEWS", "News", false);
        activeViewer();
        when(postRepository.findActiveBySlug("categorized")).thenReturn(Optional.of(post));
        when(contentCategoryRepository.findById(7L)).thenReturn(Optional.of(inactiveCategory));

        PostDetailResponse response = postService.getPostBySlug(VIEWER_EMAIL, "categorized");

        assertThat(response.getCategory()).extracting("id", "code", "name")
                .containsExactly(7L, "NEWS", "News");
        verify(contentCategoryRepository, never()).findByIdAndIsActiveTrue(any());
    }

    @Test
    void detailWithNoCategoryDoesNotLookUpCategoryMetadata() {
        PostEntity post = post(1L, VIEWER_ID, PostStatus.DRAFT, PostVisibility.LAB, null, "uncategorized", 1);
        activeViewer();
        when(postRepository.findActiveBySlug("uncategorized")).thenReturn(Optional.of(post));

        PostDetailResponse response = postService.getPostBySlug(VIEWER_EMAIL, "uncategorized");

        assertThat(response.getCategory()).isNull();
        verifyNoInteractions(contentCategoryRepository);
    }

    @Test
    void detailKeepsPostReadableWhenReferencedCategoryIsMissing() {
        PostEntity post = post(1L, VIEWER_ID, PostStatus.DRAFT, PostVisibility.LAB, 7L, "missing-category", 1);
        activeViewer();
        when(postRepository.findActiveBySlug("missing-category")).thenReturn(Optional.of(post));
        when(contentCategoryRepository.findById(7L)).thenReturn(Optional.empty());

        PostDetailResponse response = postService.getPostBySlug(VIEWER_EMAIL, "missing-category");

        assertThat(response.getCategory()).isNull();
    }

    @Test
    void detailPreservesContentJsonWithoutExposingInternalPostFields() {
        Map<String, Object> content = Map.of("type", "doc");
        PostEntity post = post(1L, VIEWER_ID, PostStatus.DRAFT, PostVisibility.LAB, null, "detail", 1, content);
        activeViewer();
        when(postRepository.findActiveBySlug("detail")).thenReturn(Optional.of(post));

        PostDetailResponse response = postService.getPostBySlug(VIEWER_EMAIL, "detail");

        assertThat(response.getContentJson()).isEqualTo(content);
        assertThat(PostDetailResponse.class.getDeclaredFields()).extracting(Field::getName)
                .doesNotContain("authorUserId", "deletedAt", "contentHtml");
        verify(postRepository, never()).save(any(PostEntity.class));
    }

    private void activeViewer() {
        when(userRepository.findByEmail(VIEWER_EMAIL)).thenReturn(Optional.of(user(VIEWER_ID, true)));
    }

    private static Stream<Arguments> readableByOwnerOrPublishedVisibility() {
        return Stream.of(
                Arguments.of(VIEWER_ID, PostStatus.DRAFT, PostVisibility.PROJECT),
                Arguments.of(99L, PostStatus.PUBLISHED, PostVisibility.PUBLIC),
                Arguments.of(99L, PostStatus.PUBLISHED, PostVisibility.LAB),
                Arguments.of(null, PostStatus.PUBLISHED, PostVisibility.PUBLIC),
                Arguments.of(null, PostStatus.PUBLISHED, PostVisibility.LAB)
        );
    }

    private static Stream<Arguments> unreadablePosts() {
        return Stream.of(
                Arguments.of(99L, PostStatus.DRAFT, PostVisibility.PUBLIC),
                Arguments.of(99L, PostStatus.PENDING_REVIEW, PostVisibility.PUBLIC),
                Arguments.of(99L, PostStatus.REVISION_REQUIRED, PostVisibility.PUBLIC),
                Arguments.of(99L, PostStatus.APPROVED, PostVisibility.PUBLIC),
                Arguments.of(99L, PostStatus.REJECTED, PostVisibility.PUBLIC),
                Arguments.of(99L, PostStatus.PUBLISHED, PostVisibility.PROJECT),
                Arguments.of(null, PostStatus.DRAFT, PostVisibility.PUBLIC),
                Arguments.of(null, PostStatus.PUBLISHED, PostVisibility.PROJECT)
        );
    }

    private static PostEntity post(Long id, Long authorId, PostStatus status, PostVisibility visibility, Long categoryId, String slug, int second) {
        return post(id, authorId, status, visibility, categoryId, slug, second,
                Map.of("id", id));
    }

    private static PostEntity post(Long id, Long authorId, PostStatus status, PostVisibility visibility, Long categoryId, String slug,
                                   int second, Map<String, Object> content) {
        try {
            Instant createdAt = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(second);
            PostEntity post = PostEntity.createDraft(authorId, "Title " + id, slug, "Excerpt", content,
                    visibility, categoryId, createdAt);
            set(post, "id", id);
            set(post, "status", status);
            return post;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void set(PostEntity post, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = PostEntity.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(post, value);
    }

    private static UserEntity user(Long id, boolean active) {
        return UserEntity.builder().id(id).email(VIEWER_EMAIL).isActive(active).build();
    }

    private static ContentCategoryEntity category(Long id, String code, String name, boolean active) {
        return ContentCategoryEntity.builder().id(id).code(code).name(name).isActive(active).build();
    }

    private static void assertUnauthorized(ThrowingCall call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private static void assertNotFound(ThrowingCall call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
