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
class PostPublishServiceImplTest {

    private static final String PUBLISHER_EMAIL = "publisher@example.edu";
    private static final Long PUBLISHER_ID = 41L;
    private static final Long POST_ID = 71L;
    private static final Instant CREATED_AT = Instant.parse("2026-08-01T10:00:00Z");

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
    void publishDoesNotUseReviewOrCrudPersistence() {
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
    void approvedPostPublishesUnderLockAndPreservesUnrelatedFields() {
        PostEntity post = postInState(PostStatus.APPROVED, 99L);
        PostSnapshot before = snapshot(post);
        activePublisher();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));
        Instant beforePublish = Instant.now();

        PostDetailResponse response = postService.publishPost(PUBLISHER_EMAIL, POST_ID);

        Instant afterPublish = Instant.now();
        verify(userRepository).findByEmail(PUBLISHER_EMAIL);
        verify(postRepository).findActiveByIdForUpdate(POST_ID);
        assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.getPublishedAt()).isNotNull().isEqualTo(post.getUpdatedAt());
        assertThat(post.getPublishedAt()).isAfterOrEqualTo(beforePublish).isBeforeOrEqualTo(afterPublish);
        assertThat(response.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(response.getPublishedAt()).isEqualTo(post.getPublishedAt());
        assertThat(response.getUpdatedAt()).isEqualTo(post.getUpdatedAt());
        assertThat(response.getTitle()).isEqualTo(before.title());
        assertThat(response.getSlug()).isEqualTo(before.slug());
        assertThat(response.getExcerpt()).isEqualTo(before.excerpt());
        assertThat(response.getContentJson()).isEqualTo(before.contentJson());
        assertThat(response.getVisibility()).isEqualTo(before.visibility());
        assertThat(snapshot(post)).isEqualTo(before.withPublication(PostStatus.PUBLISHED, post.getPublishedAt()));
    }

    @Test
    void approvedPostCanBePublishedByAnotherUserWithoutOwnershipRequirement() {
        PostEntity post = postInState(PostStatus.APPROVED, 99L);
        activePublisher();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));

        PostDetailResponse response = postService.publishPost(PUBLISHER_EMAIL, POST_ID);

        assertThat(response.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void authorlessApprovedPostCanBePublished() {
        PostEntity post = postInState(PostStatus.APPROVED, null);
        activePublisher();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));

        PostDetailResponse response = postService.publishPost(PUBLISHER_EMAIL, POST_ID);

        assertThat(response.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void missingOrDeletedPostFromLockedActiveLookupReturnsNotFound() {
        activePublisher();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.NOT_FOUND, () -> postService.publishPost(PUBLISHER_EMAIL, POST_ID));

        verify(postRepository).findActiveByIdForUpdate(POST_ID);
    }

    @ParameterizedTest
    @EnumSource(value = PostStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void nonApprovedLockedPostReturnsConflictWithoutChangingPublication(PostStatus status) {
        PostEntity post = postInState(status, 99L);
        PostSnapshot before = snapshot(post);
        activePublisher();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));

        assertStatus(HttpStatus.CONFLICT, () -> postService.publishPost(PUBLISHER_EMAIL, POST_ID));

        assertThat(snapshot(post)).isEqualTo(before);
        verify(postRepository).findActiveByIdForUpdate(POST_ID);
    }

    @Test
    void missingCanonicalPublisherReturnsUnauthorizedBeforePostLock() {
        when(userRepository.findByEmail(PUBLISHER_EMAIL)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.UNAUTHORIZED, () -> postService.publishPost(PUBLISHER_EMAIL, POST_ID));

        verifyNoInteractions(postRepository);
    }

    @Test
    void inactiveCanonicalPublisherReturnsUnauthorizedBeforePostLock() {
        when(userRepository.findByEmail(PUBLISHER_EMAIL)).thenReturn(Optional.of(user(PUBLISHER_ID, false)));

        assertStatus(HttpStatus.UNAUTHORIZED, () -> postService.publishPost(PUBLISHER_EMAIL, POST_ID));

        verifyNoInteractions(postRepository);
    }

    @Test
    void unexpectedLockedLookupFailurePropagatesWithoutConflictTranslation() {
        RuntimeException failure = new IllegalStateException("unexpected lock failure");
        activePublisher();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenThrow(failure);

        assertThatThrownBy(() -> postService.publishPost(PUBLISHER_EMAIL, POST_ID))
                .isSameAs(failure);
    }

    @Test
    void publishOperationUsesAnOrdinaryWriteTransaction() throws NoSuchMethodException {
        Method method = PostServiceImpl.class.getMethod("publishPost", String.class, Long.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    private void activePublisher() {
        when(userRepository.findByEmail(PUBLISHER_EMAIL)).thenReturn(Optional.of(user(PUBLISHER_ID, true)));
    }

    private static UserEntity user(Long id, boolean active) {
        return UserEntity.builder().id(id).email(PUBLISHER_EMAIL).isActive(active).build();
    }

    private static PostEntity postInState(PostStatus status, Long authorUserId) {
        PostEntity post = PostEntity.createDraft(
                authorUserId,
                "Original title",
                "immutable-slug",
                "Original excerpt",
                Map.of("type", "doc"),
                PostVisibility.LAB,
                null,
                CREATED_AT
        );
        setId(post);
        if (status == PostStatus.DRAFT) {
            return post;
        }

        post.submitForReview(CREATED_AT.plusSeconds(1));
        if (status == PostStatus.PENDING_REVIEW) {
            return post;
        }

        if (status == PostStatus.REVISION_REQUIRED || status == PostStatus.REJECTED) {
            post.applyReviewDecision(
                    status == PostStatus.REVISION_REQUIRED
                            ? com.smartlab.enums.ReviewDecision.REVISION_REQUIRED
                            : com.smartlab.enums.ReviewDecision.REJECTED,
                    CREATED_AT.plusSeconds(2)
            );
            return post;
        }

        if (status == PostStatus.APPROVED) {
            setStatus(post, PostStatus.APPROVED);
        } else {
            post.applyReviewDecision(com.smartlab.enums.ReviewDecision.APPROVED, CREATED_AT.plusSeconds(2));
        }
        return post;
    }

    private static void setId(PostEntity post) {
        try {
            Field field = PostEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(post, POST_ID);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void setStatus(PostEntity post, PostStatus status) {
        try {
            Field field = PostEntity.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(post, status);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static PostSnapshot snapshot(PostEntity post) {
        return new PostSnapshot(
                post.getStatus(),
                post.getTitle(),
                post.getSlug(),
                post.getExcerpt(),
                post.getContentJson(),
                post.getVisibility(),
                post.getPublishedAt(),
                post.getUpdatedAt()
        );
    }

    private static void assertStatus(HttpStatus expectedStatus, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(expectedStatus);
    }

    private record PostSnapshot(
            PostStatus status,
            String title,
            String slug,
            String excerpt,
            Map<String, Object> contentJson,
            PostVisibility visibility,
            Instant publishedAt,
            Instant updatedAt
    ) {
        private PostSnapshot withPublication(PostStatus newStatus, Instant publicationInstant) {
            return new PostSnapshot(
                    newStatus, title, slug, excerpt, contentJson, visibility, publicationInstant, publicationInstant
            );
        }
    }
}
