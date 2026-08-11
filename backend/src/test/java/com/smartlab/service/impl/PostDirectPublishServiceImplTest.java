package com.smartlab.service.impl;

import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.entity.PostEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import com.smartlab.enums.ReviewDecision;
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
class PostDirectPublishServiceImplTest {

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
    void directPublishUsesNoReviewOrAdditionalPersistenceDependencies() {
        verify(postRepository, never()).save(any(PostEntity.class));
        verify(postRepository, never()).findActiveById(any());
        verify(postRepository, never()).findOwnedActiveByIdAndStatus(any(), any(), any());
        verifyNoInteractions(
                postReviewRepository,
                contentCategoryRepository,
                postSlugGenerator,
                postCreateAttemptService,
                notificationService
        );
    }

    @Test
    void ownedDraftDirectlyPublishesUnderLockAndReturnsCanonicalDetail() throws Exception {
        PostEntity post = draft(OWNER_ID);
        setField(post, "projectId", 11L);
        setField(post, "contentHtml", "<p>Original HTML</p>");
        setField(post, "coverFileId", 12L);
        PostSnapshot before = snapshot(post);
        activeOwner();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));
        Instant beforePublish = Instant.now();

        PostDetailResponse response = postService.directPublishPost(OWNER_EMAIL, POST_ID);

        Instant afterPublish = Instant.now();
        verify(userRepository).findByEmail(OWNER_EMAIL);
        verify(postRepository).findActiveByIdForUpdate(POST_ID);
        assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.getPublishedAt()).isNotNull().isEqualTo(post.getUpdatedAt());
        assertThat(post.getPublishedAt()).isAfterOrEqualTo(beforePublish).isBeforeOrEqualTo(afterPublish);
        assertThat(response.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(response.getPublishedAt()).isEqualTo(post.getPublishedAt());
        assertThat(response.getUpdatedAt()).isEqualTo(post.getUpdatedAt());
        assertThat(snapshot(post)).isEqualTo(before.withPublication(post.getPublishedAt()));
    }

    @Test
    void nonOwnerCannotDirectlyPublishLockedDraft() {
        PostEntity post = draft(99L);

        assertEligibilityFailureDoesNotMutate(post, HttpStatus.FORBIDDEN);
    }

    @Test
    void nonOwnerReceivesForbiddenBeforeDirectPublishStateConflict() {
        PostEntity post = postInState(PostStatus.APPROVED, 99L);

        assertEligibilityFailureDoesNotMutate(post, HttpStatus.FORBIDDEN);
    }

    @Test
    void authorlessPostCannotDirectlyPublishLockedDraft() throws Exception {
        PostEntity post = draft(OWNER_ID);
        setField(post, "authorUserId", null);

        assertEligibilityFailureDoesNotMutate(post, HttpStatus.FORBIDDEN);
    }

    @Test
    void missingOrDeletedPostFromLockedActiveLookupReturnsNotFound() {
        activeOwner();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.NOT_FOUND, () -> postService.directPublishPost(OWNER_EMAIL, POST_ID));

        verify(postRepository).findActiveByIdForUpdate(POST_ID);
    }

    @ParameterizedTest
    @EnumSource(value = PostStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void ownedLockedNonDraftPostReturnsConflictWithoutDirectPublication(PostStatus status) {
        PostEntity post = postInState(status, OWNER_ID);
        PostSnapshot before = snapshot(post);
        activeOwner();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));

        assertStatus(HttpStatus.CONFLICT, () -> postService.directPublishPost(OWNER_EMAIL, POST_ID));

        verify(postRepository).findActiveByIdForUpdate(POST_ID);
        assertThat(snapshot(post)).isEqualTo(before);
    }

    @Test
    void missingCanonicalOwnerReturnsUnauthorizedBeforePostLock() {
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.UNAUTHORIZED, () -> postService.directPublishPost(OWNER_EMAIL, POST_ID));

        verifyNoInteractions(postRepository);
    }

    @Test
    void inactiveCanonicalOwnerReturnsUnauthorizedBeforePostLock() {
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(user(OWNER_ID, false)));

        assertStatus(HttpStatus.UNAUTHORIZED, () -> postService.directPublishPost(OWNER_EMAIL, POST_ID));

        verifyNoInteractions(postRepository);
    }

    @Test
    void unexpectedLockedLookupFailurePropagatesWithoutConflictTranslation() {
        RuntimeException failure = new IllegalStateException("unexpected lock failure");
        activeOwner();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenThrow(failure);

        assertThatThrownBy(() -> postService.directPublishPost(OWNER_EMAIL, POST_ID))
                .isSameAs(failure);
    }

    @Test
    void directPublishOperationUsesAnOrdinaryWriteTransaction() throws NoSuchMethodException {
        Method method = PostServiceImpl.class.getMethod("directPublishPost", String.class, Long.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    private void assertEligibilityFailureDoesNotMutate(PostEntity post, HttpStatus expectedStatus) {
        PostSnapshot before = snapshot(post);
        activeOwner();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));

        assertStatus(expectedStatus, () -> postService.directPublishPost(OWNER_EMAIL, POST_ID));

        verify(postRepository).findActiveByIdForUpdate(POST_ID);
        assertThat(snapshot(post)).isEqualTo(before);
    }

    private void activeOwner() {
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(user(OWNER_ID, true)));
    }

    private static UserEntity user(Long id, boolean active) {
        return UserEntity.builder().id(id).email(OWNER_EMAIL).isActive(active).build();
    }

    private static PostEntity draft(Long authorUserId) {
        PostEntity post = PostEntity.createDraft(
                authorUserId,
                "Original title",
                "immutable-slug",
                "Original excerpt",
                new LinkedHashMap<>(Map.of("type", "doc", "body", "Original body")),
                PostVisibility.LAB,
                null,
                Instant.parse("2026-08-01T10:00:00Z")
        );
        try {
            setField(post, "id", POST_ID);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        return post;
    }

    private static PostEntity postInState(PostStatus status, Long authorUserId) {
        PostEntity post = draft(authorUserId);
        if (status == PostStatus.DRAFT) {
            return post;
        }

        Instant createdAt = post.getCreatedAt();
        post.submitForReview(createdAt.plusSeconds(1));
        if (status == PostStatus.PENDING_REVIEW) {
            return post;
        }
        if (status == PostStatus.REVISION_REQUIRED || status == PostStatus.REJECTED) {
            post.applyReviewDecision(
                    status == PostStatus.REVISION_REQUIRED ? ReviewDecision.REVISION_REQUIRED : ReviewDecision.REJECTED,
                    createdAt.plusSeconds(2)
            );
            return post;
        }

        if (status == PostStatus.APPROVED) {
            setStatus(post, PostStatus.APPROVED);
        } else {
            post.applyReviewDecision(ReviewDecision.APPROVED, createdAt.plusSeconds(2));
        }
        return post;
    }

    private static void setStatus(PostEntity post, PostStatus status) {
        try {
            setField(post, "status", status);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void setField(PostEntity post, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = PostEntity.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(post, value);
    }

    private static PostSnapshot snapshot(PostEntity post) {
        return new PostSnapshot(
                post.getId(),
                post.getAuthorUserId(),
                post.getProjectId(),
                post.getTitle(),
                post.getSlug(),
                post.getExcerpt(),
                post.getContentJson(),
                post.getContentHtml(),
                post.getCoverFileId(),
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
            Long projectId,
            String title,
            String slug,
            String excerpt,
            Map<String, Object> contentJson,
            String contentHtml,
            Long coverFileId,
            PostVisibility visibility,
            Long categoryId,
            PostStatus status,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt
    ) {
        private PostSnapshot withPublication(Instant publicationInstant) {
            return new PostSnapshot(
                    id, authorUserId, projectId, title, slug, excerpt, contentJson, contentHtml, coverFileId,
                    visibility, categoryId, PostStatus.PUBLISHED, publicationInstant, createdAt, publicationInstant, deletedAt
            );
        }
    }
}
