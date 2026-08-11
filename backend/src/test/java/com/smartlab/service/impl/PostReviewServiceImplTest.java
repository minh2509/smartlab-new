package com.smartlab.service.impl;

import com.smartlab.dto.request.ReviewPostRequest;
import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.entity.PostEntity;
import com.smartlab.entity.PostReviewEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import com.smartlab.enums.ReviewDecision;
import com.smartlab.repo.ContentCategoryRepository;
import com.smartlab.repo.PostRepository;
import com.smartlab.repo.PostReviewRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.NotificationService;
import com.smartlab.service.AuditService;
import com.smartlab.service.NotificationRelated;
import com.smartlab.service.PostService;
import com.smartlab.service.PostSlugGenerator;
import com.smartlab.service.PostContentRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostReviewServiceImplTest {

    private static final String REVIEWER_EMAIL = "reviewer@example.edu";
    private static final Long REVIEWER_ID = 41L;
    private static final Long AUTHOR_ID = 99L;
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
    private NotificationService notificationService;
    @Mock
    private AuditService auditService;
    @Mock
    private PostSlugGenerator postSlugGenerator;
    @Mock
    private PostCreateAttemptService postCreateAttemptService;

    @Mock
    private PostContentRenderer postContentRenderer;
    @Mock
    private com.smartlab.repo.ProjectRepository projectRepository;
    @Mock
    private com.smartlab.repo.ProjectMemberRepository projectMemberRepository;

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
    void reviewDoesNotUseCrudPersistenceOrUnapprovedDependencies() {
        verify(postRepository, never()).save(any(PostEntity.class));
        verify(postRepository, never()).findActiveById(any());
        verify(postRepository, never()).findOwnedActiveByIdAndStatus(any(), any(), any());
        verify(postReviewRepository, never()).delete(any(PostReviewEntity.class));
        verifyNoInteractions(contentCategoryRepository, postSlugGenerator, postCreateAttemptService);
    }

    @Test
    void approvedReviewPersistsTrustedHistoryTransitionsLockedPostAndReturnsCanonicalDetail() {
        assertSuccessfulReview(
                ReviewDecision.APPROVED,
                "approved with evidence",
                PostStatus.PUBLISHED,
                AUTHOR_ID,
                "Bài viết của bạn đã được duyệt và xuất bản."
        );
    }

    @Test
    void revisionRequiredReviewPersistsReasonAndUsesOneCoherentEventTimestamp() {
        assertSuccessfulReview(
                ReviewDecision.REVISION_REQUIRED,
                "Please add sources",
                PostStatus.REVISION_REQUIRED,
                AUTHOR_ID,
                "Bài viết của bạn cần được chỉnh sửa."
        );
    }

    @Test
    void rejectedReviewPreservesLeadingAndTrailingWhitespaceExactly() {
        assertSuccessfulReview(
                ReviewDecision.REJECTED,
                "  needs evidence  ",
                PostStatus.REJECTED,
                AUTHOR_ID,
                "Bài viết của bạn đã bị từ chối."
        );
    }

    @Test
    void authorlessPendingReviewCanBeReviewedByTrustedCanonicalUser() {
        assertSuccessfulReview(ReviewDecision.APPROVED, null, PostStatus.PUBLISHED, null, null);
    }

    @Test
    void selfReviewReturnsForbiddenWithoutTransitionOrHistoryPersistence() {
        PostEntity post = postInState(PostStatus.PENDING_REVIEW, REVIEWER_ID);
        PostSnapshot before = snapshot(post);
        activeReviewer();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));

        assertStatus(HttpStatus.FORBIDDEN,
                () -> postService.reviewPost(REVIEWER_EMAIL, POST_ID, request(ReviewDecision.APPROVED, null)));

        assertThat(snapshot(post)).isEqualTo(before);
        verify(postReviewRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void missingOrDeletedPostFromLockedActiveLookupReturnsNotFoundWithoutHistoryPersistence() {
        activeReviewer();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.NOT_FOUND,
                () -> postService.reviewPost(REVIEWER_EMAIL, POST_ID, request(ReviewDecision.APPROVED, null)));

        verify(postRepository).findActiveByIdForUpdate(POST_ID);
        verify(postReviewRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @ParameterizedTest
    @EnumSource(value = PostStatus.class, names = "PENDING_REVIEW", mode = EnumSource.Mode.EXCLUDE)
    void nonPendingLockedPostReturnsConflictWithoutTransitionOrHistoryPersistence(PostStatus status) {
        PostEntity post = postInState(status, AUTHOR_ID);
        PostSnapshot before = snapshot(post);
        activeReviewer();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));

        assertStatus(HttpStatus.CONFLICT,
                () -> postService.reviewPost(REVIEWER_EMAIL, POST_ID, request(ReviewDecision.APPROVED, null)));

        assertThat(snapshot(post)).isEqualTo(before);
        verify(postReviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void missingCanonicalReviewerReturnsUnauthorizedBeforePostLock() {
        when(userRepository.findByEmail(REVIEWER_EMAIL)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.UNAUTHORIZED,
                () -> postService.reviewPost(REVIEWER_EMAIL, POST_ID, request(ReviewDecision.APPROVED, null)));

        verifyNoInteractions(postRepository);
        verify(postReviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void inactiveCanonicalReviewerReturnsUnauthorizedBeforePostLock() {
        when(userRepository.findByEmail(REVIEWER_EMAIL)).thenReturn(Optional.of(user(REVIEWER_ID, false)));

        assertStatus(HttpStatus.UNAUTHORIZED,
                () -> postService.reviewPost(REVIEWER_EMAIL, POST_ID, request(ReviewDecision.APPROVED, null)));

        verifyNoInteractions(postRepository);
        verify(postReviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void unexpectedReviewPersistenceFailurePropagatesWithoutGenericConflictTranslation() {
        PostEntity post = postInState(PostStatus.PENDING_REVIEW, AUTHOR_ID);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("unexpected persistence failure");
        activeReviewer();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));
        when(postReviewRepository.saveAndFlush(any(PostReviewEntity.class))).thenThrow(failure);

        assertThatThrownBy(() -> postService.reviewPost(
                REVIEWER_EMAIL,
                POST_ID,
                request(ReviewDecision.APPROVED, "approved")
        )).isSameAs(failure);

        verify(postReviewRepository).saveAndFlush(any(PostReviewEntity.class));
        verifyNoInteractions(notificationService);
        verifyNoInteractions(auditService);
    }

    @Test
    void notificationPersistenceFailurePropagatesAfterReviewHistoryFlush() {
        PostEntity post = postInState(PostStatus.PENDING_REVIEW, AUTHOR_ID);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("notification persistence failure");
        activeReviewer();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));
        doThrow(failure).when(notificationService).notify(
                any(),
                any(),
                any(),
                any(),
                any()
        );

        assertThatThrownBy(() -> postService.reviewPost(
                REVIEWER_EMAIL,
                POST_ID,
                request(ReviewDecision.APPROVED, "approved")
        )).isSameAs(failure);

        InOrder order = inOrder(postReviewRepository, notificationService);
        order.verify(postReviewRepository).saveAndFlush(any(PostReviewEntity.class));
        order.verify(notificationService).notify(
                AUTHOR_ID,
                "POST_REVIEW_APPROVED",
                "Bài viết của bạn đã được duyệt và xuất bản.",
                new NotificationRelated(REVIEWER_ID, "POST", POST_ID, "/posts/immutable-slug"),
                post.getUpdatedAt()
        );
        verifyNoInteractions(auditService);
    }

    @Test
    void auditFailurePropagatesAfterNotification() {
        PostEntity post = postInState(PostStatus.PENDING_REVIEW, AUTHOR_ID);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("audit persistence failure");
        activeReviewer();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));
        doThrow(failure).when(auditService).log(any(), any(), any(), any(), any());
        assertThatThrownBy(() -> postService.reviewPost(
                REVIEWER_EMAIL, POST_ID, request(ReviewDecision.APPROVED, "approved"))).isSameAs(failure);
        verify(notificationService).notify(any(), any(), any(), any(), any());
    }

    @Test
    void reviewOperationUsesAnOrdinaryWriteTransaction() throws NoSuchMethodException {
        Method method = PostServiceImpl.class.getMethod(
                "reviewPost",
                String.class,
                Long.class,
                ReviewPostRequest.class
        );

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    private void assertSuccessfulReview(
            ReviewDecision decision,
            String reason,
            PostStatus expectedStatus,
            Long authorUserId,
            String expectedNotificationMessage
    ) {
        PostEntity post = postInState(PostStatus.PENDING_REVIEW, authorUserId);
        activeReviewer();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));
        Instant beforeReview = Instant.now();

        PostDetailResponse response = postService.reviewPost(REVIEWER_EMAIL, POST_ID, request(decision, reason));

        Instant afterReview = Instant.now();
        ArgumentCaptor<PostReviewEntity> reviewCaptor = ArgumentCaptor.forClass(PostReviewEntity.class);
        InOrder order = inOrder(userRepository, postRepository, postReviewRepository, notificationService);
        order.verify(userRepository).findByEmail(REVIEWER_EMAIL);
        order.verify(postRepository).findActiveByIdForUpdate(POST_ID);
        order.verify(postReviewRepository).saveAndFlush(reviewCaptor.capture());
        PostReviewEntity review = reviewCaptor.getValue();

        if (authorUserId == null) {
            verifyNoInteractions(notificationService);
        } else {
            order.verify(notificationService).notify(
                    authorUserId,
                    "POST_REVIEW_" + decision.name(),
                    expectedNotificationMessage,
                    new NotificationRelated(REVIEWER_ID, "POST", POST_ID, "/posts/immutable-slug"),
                    review.getCreatedAt()
            );
        }
        verify(auditService).log(
                "POST_REVIEWED",
                "POST",
                POST_ID.toString(),
                Map.of("status", "PENDING_REVIEW"),
                Map.of("status", expectedStatus.name(), "decision", decision.name())
        );

        assertThat(review.getId()).isNull();
        assertThat(review.getPostId()).isEqualTo(POST_ID);
        assertThat(review.getReviewerUserId()).isEqualTo(REVIEWER_ID);
        assertThat(review.getDecision()).isEqualTo(decision);
        assertThat(review.getReason()).isEqualTo(reason);
        assertThat(review.getCreatedAt()).isEqualTo(post.getUpdatedAt());
        assertThat(review.getCreatedAt()).isAfterOrEqualTo(beforeReview).isBeforeOrEqualTo(afterReview);
        assertThat(post.getStatus()).isEqualTo(expectedStatus);
        assertThat(response.getStatus()).isEqualTo(expectedStatus);
        assertThat(response.getUpdatedAt()).isEqualTo(post.getUpdatedAt());
        if (decision == ReviewDecision.APPROVED) {
            assertThat(post.getPublishedAt()).isEqualTo(review.getCreatedAt());
            assertThat(response.getPublishedAt()).isEqualTo(review.getCreatedAt());
        } else {
            assertThat(post.getPublishedAt()).isNull();
            assertThat(response.getPublishedAt()).isNull();
        }
        assertThat(response.getId()).isEqualTo(POST_ID);
        assertThat(response.getTitle()).isEqualTo("Original title");
    }

    private void activeReviewer() {
        when(userRepository.findByEmail(REVIEWER_EMAIL)).thenReturn(Optional.of(user(REVIEWER_ID, true)));
    }

    private static ReviewPostRequest request(ReviewDecision decision, String reason) {
        return new ReviewPostRequest(decision, reason);
    }

    private static UserEntity user(Long id, boolean active) {
        return UserEntity.builder().id(id).email(REVIEWER_EMAIL).isActive(active).build();
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

        if (status == PostStatus.APPROVED) {
            setStatus(post, PostStatus.APPROVED);
            return post;
        }

        ReviewDecision decision = switch (status) {
            case PUBLISHED -> ReviewDecision.APPROVED;
            case REVISION_REQUIRED -> ReviewDecision.REVISION_REQUIRED;
            case REJECTED -> ReviewDecision.REJECTED;
            default -> throw new IllegalArgumentException("Unsupported test status: " + status);
        };
        post.applyReviewDecision(decision, CREATED_AT.plusSeconds(2));
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
        return new PostSnapshot(post.getStatus(), post.getUpdatedAt(), post.getPublishedAt());
    }

    private static void assertStatus(HttpStatus expectedStatus, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(expectedStatus);
    }

    private record PostSnapshot(PostStatus status, Instant updatedAt, Instant publishedAt) {
    }
}
