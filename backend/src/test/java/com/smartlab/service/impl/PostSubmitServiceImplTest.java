package com.smartlab.service.impl;

import com.smartlab.dto.response.PostDetailResponse;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostSubmitServiceImplTest {

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
    @Mock private com.smartlab.repo.ProjectRepository projectRepository;
    @Mock private com.smartlab.repo.ProjectMemberRepository projectMemberRepository;

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
                auditService,
                projectRepository,
                projectMemberRepository
        );
    }

    @AfterEach
    void submitUsesNoOtherPostPersistenceOrSideEffectDependency() {
        verify(postRepository, never()).save(any(PostEntity.class));
        verify(postRepository, never()).findOwnedActiveByIdAndStatus(any(), any(), any());
        verify(postRepository, never()).findActiveById(any());
        verifyNoInteractions(contentCategoryRepository, postSlugGenerator, postCreateAttemptService, notificationService);
    }

    @Test
    void ownedActiveDraftTransitionsUnderLockAndReturnsCanonicalDetailWithoutChangingOtherFields() {
        PostEntity post = draft();
        PostSnapshot before = snapshot(post);
        activeOwner();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));
        Instant beforeSubmit = Instant.now();

        PostDetailResponse response = postService.submitForReview(OWNER_EMAIL, POST_ID);

        Instant afterSubmit = Instant.now();
        verify(userRepository).findByEmail(OWNER_EMAIL);
        verify(postRepository).findActiveByIdForUpdate(POST_ID);
        assertThat(post.getStatus()).isEqualTo(PostStatus.PENDING_REVIEW);
        assertThat(post.getUpdatedAt()).isAfterOrEqualTo(beforeSubmit).isBeforeOrEqualTo(afterSubmit);
        assertThat(response).extracting(
                        PostDetailResponse::getId,
                        PostDetailResponse::getTitle,
                        PostDetailResponse::getSlug,
                        PostDetailResponse::getExcerpt,
                        PostDetailResponse::getContentJson,
                        PostDetailResponse::getVisibility,
                        PostDetailResponse::getStatus,
                        PostDetailResponse::getPublishedAt,
                        PostDetailResponse::getCreatedAt,
                        PostDetailResponse::getUpdatedAt
                )
                .containsExactly(
                        before.id(),
                        before.title(),
                        before.slug(),
                        before.excerpt(),
                        before.contentJson(),
                        before.visibility(),
                        PostStatus.PENDING_REVIEW,
                        before.publishedAt(),
                        before.createdAt(),
                        post.getUpdatedAt()
                );
        assertThat(snapshot(post)).isEqualTo(before.withStatusAndUpdatedAt(PostStatus.PENDING_REVIEW, post.getUpdatedAt()));
    }

    @Test
    void missingOrSoftDeletedPostFromLockedActiveLookupReturnsNotFound() {
        activeOwner();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.NOT_FOUND, () -> postService.submitForReview(OWNER_EMAIL, POST_ID));

        verify(postRepository).findActiveByIdForUpdate(POST_ID);
    }

    @Test
    void authorlessLockedDraftReturnsForbiddenWithoutTransition() throws ReflectiveOperationException {
        PostEntity post = draft();
        set(post, "authorUserId", null);

        assertEligibilityFailureDoesNotMutate(post, HttpStatus.FORBIDDEN);
    }

    @Test
    void otherOwnersLockedDraftReturnsForbiddenWithoutTransition() throws ReflectiveOperationException {
        PostEntity post = draft();
        set(post, "authorUserId", 99L);

        assertEligibilityFailureDoesNotMutate(post, HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @EnumSource(value = PostStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void ownedLockedNonDraftReturnsConflictWithoutInvokingTransition(PostStatus status) throws ReflectiveOperationException {
        PostEntity post = draft();
        set(post, "status", status);

        assertEligibilityFailureDoesNotMutate(post, HttpStatus.CONFLICT);
    }

    @Test
    void missingCanonicalUserReturnsUnauthorizedBeforeLockedLookup() {
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.UNAUTHORIZED, () -> postService.submitForReview(OWNER_EMAIL, POST_ID));

        verify(postRepository, never()).findActiveByIdForUpdate(any());
    }

    @Test
    void inactiveCanonicalUserReturnsUnauthorizedBeforeLockedLookup() {
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(user(OWNER_ID, false)));

        assertStatus(HttpStatus.UNAUTHORIZED, () -> postService.submitForReview(OWNER_EMAIL, POST_ID));

        verify(postRepository, never()).findActiveByIdForUpdate(any());
    }

    @Test
    void submitOperationUsesAnOrdinaryWriteTransaction() throws NoSuchMethodException {
        Method method = PostServiceImpl.class.getMethod("submitForReview", String.class, Long.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    private void assertEligibilityFailureDoesNotMutate(PostEntity post, HttpStatus expectedStatus) {
        PostSnapshot before = snapshot(post);
        activeOwner();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));

        assertStatus(expectedStatus, () -> postService.submitForReview(OWNER_EMAIL, POST_ID));

        verify(postRepository).findActiveByIdForUpdate(POST_ID);
        assertThat(snapshot(post)).isEqualTo(before);
    }

    private void activeOwner() {
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(user(OWNER_ID, true)));
    }

    private static UserEntity user(Long id, boolean active) {
        return UserEntity.builder().id(id).email(OWNER_EMAIL).isActive(active).build();
    }

    private static PostEntity draft() {
        PostEntity post = PostEntity.createDraft(
                OWNER_ID,
                "Original title",
                "immutable-slug",
                "Original excerpt",
                new LinkedHashMap<>(Map.of("type", "doc", "body", "Original body")),
                PostVisibility.LAB,
                null,
                Instant.parse("2026-08-01T10:00:00Z")
        );
        try {
            set(post, "id", POST_ID);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        return post;
    }

    private static void set(PostEntity post, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = PostEntity.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(post, value);
    }

    private static PostSnapshot snapshot(PostEntity post) {
        return new PostSnapshot(
                post.getId(),
                post.getAuthorUserId(),
                post.getTitle(),
                post.getSlug(),
                post.getExcerpt(),
                post.getContentJson(),
                post.getVisibility(),
                post.getCategoryId(),
                post.getStatus(),
                post.getPublishedAt(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getDeletedAt()
        );
    }

    private static void assertStatus(HttpStatus expectedStatus, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(expectedStatus);
    }

    private record PostSnapshot(
            Long id,
            Long authorUserId,
            String title,
            String slug,
            String excerpt,
            Map<String, Object> contentJson,
            PostVisibility visibility,
            Long categoryId,
            PostStatus status,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt
    ) {
        private PostSnapshot withStatusAndUpdatedAt(PostStatus newStatus, Instant newUpdatedAt) {
            return new PostSnapshot(
                    id, authorUserId, title, slug, excerpt, contentJson, visibility, categoryId,
                    newStatus, publishedAt, createdAt, newUpdatedAt, deletedAt
            );
        }
    }
}
