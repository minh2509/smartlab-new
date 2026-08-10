package com.smartlab.service.impl;

import com.smartlab.entity.PostEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import com.smartlab.repo.ContentCategoryRepository;
import com.smartlab.repo.PostRepository;
import com.smartlab.repo.PostReviewRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.NotificationService;
import com.smartlab.service.PostService;
import com.smartlab.service.PostSlugGenerator;
import com.smartlab.service.PostContentRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostDeleteServiceImplTest {

    private static final String OWNER_EMAIL = "owner@example.edu";
    private static final Long OWNER_ID = 41L;
    private static final Long POST_ID = 71L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private ContentCategoryRepository contentCategoryRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private PostReviewRepository postReviewRepository;
    @Mock
    private PostSlugGenerator postSlugGenerator;
    @Mock
    private PostCreateAttemptService postCreateAttemptService;

    @Mock
    private PostContentRenderer postContentRenderer;
    @Mock
    private NotificationService notificationService;
    @Mock private com.smartlab.service.AuditService auditService;

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostServiceImpl(
                userRepository,
                contentCategoryRepository,
                postRepository,
                postReviewRepository,
                postSlugGenerator,
                postCreateAttemptService,
                postContentRenderer,
                notificationService,
                auditService
        );
    }

    @AfterEach
    void deleteNeverUsesEntityMutationOrPhysicalDeleteApis() {
        verifyNoInteractions(contentCategoryRepository, postSlugGenerator, postCreateAttemptService);
        verify(postRepository, never()).save(any(PostEntity.class));
        verify(postRepository, never()).delete(any(PostEntity.class));
        verify(postRepository, never()).deleteById(any());
    }

    @Test
    void trustedActiveOwnerDeletesThroughOneConditionalUpdateWithoutPreRead() {
        activeOwner();
        when(postRepository.softDeleteOwnedDraft(any(), any(), any())).thenReturn(1);

        postService.deletePost(OWNER_EMAIL, POST_ID);

        ArgumentCaptor<Instant> transitionInstant = ArgumentCaptor.forClass(Instant.class);
        InOrder order = inOrder(userRepository, postRepository);
        order.verify(userRepository).findByEmail(OWNER_EMAIL);
        order.verify(postRepository).softDeleteOwnedDraft(eq(POST_ID), eq(OWNER_ID), transitionInstant.capture());
        assertThat(transitionInstant.getValue()).isNotNull();
        verify(postRepository, never()).findActiveById(any());
    }

    @Test
    void missingTrustedUserReturnsUnauthorizedWithoutAttemptingMutation() {
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.UNAUTHORIZED, () -> postService.deletePost(OWNER_EMAIL, POST_ID));

        verifyNoInteractions(postRepository);
    }

    @Test
    void inactiveTrustedUserReturnsUnauthorizedWithoutAttemptingMutation() {
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(user(OWNER_ID, false)));

        assertStatus(HttpStatus.UNAUTHORIZED, () -> postService.deletePost(OWNER_EMAIL, POST_ID));

        verifyNoInteractions(postRepository);
    }

    @ParameterizedTest(name = "{0} active lookup absence returns not found")
    @MethodSource("unavailableActivePostScenarios")
    void zeroRowUpdateClassifiesMissingDeletedAndConcurrentDeleteLoserAsNotFound(String scenario) {
        activeOwner();
        when(postRepository.softDeleteOwnedDraft(any(), any(), any())).thenReturn(0);
        when(postRepository.findActiveById(POST_ID)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.NOT_FOUND, () -> postService.deletePost(OWNER_EMAIL, POST_ID));

        verify(postRepository).softDeleteOwnedDraft(any(), any(), any());
        verify(postRepository).findActiveById(POST_ID);
    }

    @Test
    void zeroRowUpdateWithAuthorlessActivePostReturnsForbidden() throws ReflectiveOperationException {
        PostEntity post = post(null, PostStatus.DRAFT);
        assertZeroRowClassification(post, HttpStatus.FORBIDDEN);
    }

    @Test
    void zeroRowUpdateWithAnotherOwnersActivePostReturnsForbidden() throws ReflectiveOperationException {
        PostEntity post = post(99L, PostStatus.DRAFT);
        assertZeroRowClassification(post, HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @EnumSource(value = PostStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void zeroRowUpdateWithOwnedNonDraftPostReturnsConflict(PostStatus status) throws ReflectiveOperationException {
        assertZeroRowClassification(post(OWNER_ID, status), HttpStatus.CONFLICT);
    }

    @Test
    void zeroRowUpdateWithOwnedDraftStillPresentReturnsConflict() throws ReflectiveOperationException {
        assertZeroRowClassification(post(OWNER_ID, PostStatus.DRAFT), HttpStatus.CONFLICT);
    }

    @Test
    void unexpectedMultipleAffectedRowsIsNotMisclassifiedAsBusinessFailure() {
        activeOwner();
        when(postRepository.softDeleteOwnedDraft(any(), any(), any())).thenReturn(2);

        assertThatThrownBy(() -> postService.deletePost(OWNER_EMAIL, POST_ID))
                .isInstanceOf(IllegalStateException.class);

        verify(postRepository, never()).findActiveById(any());
    }

    @Test
    void deletePostUsesOrdinaryWriteTransaction() throws NoSuchMethodException {
        Method method = PostServiceImpl.class.getMethod("deletePost", String.class, Long.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    private void activeOwner() {
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(user(OWNER_ID, true)));
    }

    private void assertZeroRowClassification(PostEntity post, HttpStatus expectedStatus) {
        PostSnapshot before = snapshot(post);
        activeOwner();
        when(postRepository.softDeleteOwnedDraft(any(), any(), any())).thenReturn(0);
        when(postRepository.findActiveById(POST_ID)).thenReturn(Optional.of(post));

        assertStatus(expectedStatus, () -> postService.deletePost(OWNER_EMAIL, POST_ID));

        assertThat(snapshot(post)).isEqualTo(before);
        verify(postRepository).softDeleteOwnedDraft(any(), any(), any());
        verify(postRepository).findActiveById(POST_ID);
    }

    private static Stream<String> unavailableActivePostScenarios() {
        return Stream.of("missing", "already deleted", "concurrent delete loser");
    }

    private static UserEntity user(Long id, boolean active) {
        return UserEntity.builder().id(id).email(OWNER_EMAIL).isActive(active).build();
    }

    private static PostEntity post(Long authorUserId, PostStatus status) throws ReflectiveOperationException {
        PostEntity post = PostEntity.createDraft(
                authorUserId,
                "Title",
                "slug",
                "Excerpt",
                Map.of(),
                PostVisibility.LAB,
                null,
                Instant.parse("2026-08-01T10:00:00Z")
        );
        set(post, "id", POST_ID);
        set(post, "status", status);
        return post;
    }

    private static void set(PostEntity post, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = PostEntity.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(post, value);
    }

    private static PostSnapshot snapshot(PostEntity post) {
        return new PostSnapshot(
                post.getAuthorUserId(),
                post.getProjectId(),
                post.getCategoryId(),
                post.getTitle(),
                post.getSlug(),
                post.getExcerpt(),
                post.getContentJson(),
                post.getContentHtml(),
                post.getCoverFileId(),
                post.getVisibility(),
                post.getStatus(),
                post.getPublishedAt(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getDeletedAt()
        );
    }

    private static void assertStatus(HttpStatus expectedStatus, ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(expectedStatus));
    }

    private record PostSnapshot(
            Long authorUserId,
            Long projectId,
            Long categoryId,
            String title,
            String slug,
            String excerpt,
            Map<String, Object> contentJson,
            String contentHtml,
            Long coverFileId,
            PostVisibility visibility,
            PostStatus status,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt
    ) {
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
