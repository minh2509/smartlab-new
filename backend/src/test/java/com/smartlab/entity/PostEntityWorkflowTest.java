package com.smartlab.entity;

import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import com.smartlab.enums.ReviewDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostEntityWorkflowTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-09T10:00:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-09T10:01:00Z");
    private static final Instant REVIEWED_AT = Instant.parse("2026-08-09T10:02:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-09T10:03:00Z");

    @Test
    void submitsDraftWithTheSuppliedTimestamp() {
        PostEntity post = draft();

        post.submitForReview(SUBMITTED_AT);

        assertThat(post.getStatus()).isEqualTo(PostStatus.PENDING_REVIEW);
        assertThat(post.getUpdatedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(post.getPublishedAt()).isNull();
    }

    @ParameterizedTest
    @MethodSource("nonDraftStatuses")
    void rejectsSubmitFromEveryNonDraftState(PostStatus sourceStatus) throws Exception {
        PostEntity post = draftWithStatus(sourceStatus);

        assertThatThrownBy(() -> post.submitForReview(SUBMITTED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only draft posts can be submitted for review");
        assertThat(post.getStatus()).isEqualTo(sourceStatus);
        assertThat(post.getUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void appliesApprovedReviewWithTheSuppliedTimestamp() {
        PostEntity post = pendingReview();

        post.applyReviewDecision(ReviewDecision.APPROVED, REVIEWED_AT);

        assertThat(post.getStatus()).isEqualTo(PostStatus.APPROVED);
        assertThat(post.getUpdatedAt()).isEqualTo(REVIEWED_AT);
        assertThat(post.getPublishedAt()).isNull();
    }

    @Test
    void appliesRevisionRequiredReviewWithTheSuppliedTimestamp() {
        PostEntity post = pendingReview();

        post.applyReviewDecision(ReviewDecision.REVISION_REQUIRED, REVIEWED_AT);

        assertThat(post.getStatus()).isEqualTo(PostStatus.REVISION_REQUIRED);
        assertThat(post.getUpdatedAt()).isEqualTo(REVIEWED_AT);
        assertThat(post.getPublishedAt()).isNull();
    }

    @Test
    void appliesRejectedReviewWithTheSuppliedTimestamp() {
        PostEntity post = pendingReview();

        post.applyReviewDecision(ReviewDecision.REJECTED, REVIEWED_AT);

        assertThat(post.getStatus()).isEqualTo(PostStatus.REJECTED);
        assertThat(post.getUpdatedAt()).isEqualTo(REVIEWED_AT);
        assertThat(post.getPublishedAt()).isNull();
    }

    @ParameterizedTest
    @MethodSource("reviewDecisionAndInvalidSourceStatuses")
    void rejectsEveryReviewDecisionOutsidePendingReview(ReviewDecision decision, PostStatus sourceStatus) throws Exception {
        PostEntity post = draftWithStatus(sourceStatus);

        assertThatThrownBy(() -> post.applyReviewDecision(decision, REVIEWED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only pending-review posts can be reviewed");
        assertThat(post.getStatus()).isEqualTo(sourceStatus);
        assertThat(post.getUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void publishesApprovedPostWithOneSuppliedTimestampForBothPublicationFields() {
        PostEntity post = approvedPost();

        post.publish(PUBLISHED_AT);

        assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(post.getUpdatedAt()).isEqualTo(PUBLISHED_AT);
    }

    @ParameterizedTest
    @MethodSource("nonApprovedStatuses")
    void rejectsPublishFromEveryNonApprovedState(PostStatus sourceStatus) throws Exception {
        PostEntity post = draftWithStatus(sourceStatus);

        assertThatThrownBy(() -> post.publish(PUBLISHED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only approved posts can be published");
        assertThat(post.getStatus()).isEqualTo(sourceStatus);
        assertThat(post.getPublishedAt()).isNull();
        assertThat(post.getUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void rejectsRepeatedPublishWithoutReplacingTheOriginalPublicationTime() {
        PostEntity post = approvedPost();
        post.publish(PUBLISHED_AT);
        Instant laterAttempt = PUBLISHED_AT.plusSeconds(1);

        assertThatThrownBy(() -> post.publish(laterAttempt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only approved posts can be published");
        assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(post.getUpdatedAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    void directlyPublishesDraftWithOneSuppliedTimestampForBothPublicationFields() {
        PostEntity post = draft();

        post.publishDirect(PUBLISHED_AT);

        assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(post.getUpdatedAt()).isEqualTo(PUBLISHED_AT);
    }

    @ParameterizedTest
    @MethodSource("nonDraftStatuses")
    void rejectsDirectPublishFromEveryNonDraftState(PostStatus sourceStatus) throws Exception {
        PostEntity post = draftWithStatus(sourceStatus);

        assertThatThrownBy(() -> post.publishDirect(PUBLISHED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only draft posts can be directly published");
        assertThat(post.getStatus()).isEqualTo(sourceStatus);
        assertThat(post.getPublishedAt()).isNull();
        assertThat(post.getUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void rejectsRepeatedDirectPublishWithoutReplacingTheOriginalPublicationTime() {
        PostEntity post = draft();
        post.publishDirect(PUBLISHED_AT);
        Instant laterAttempt = PUBLISHED_AT.plusSeconds(1);

        assertThatThrownBy(() -> post.publishDirect(laterAttempt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only draft posts can be directly published");
        assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(post.getUpdatedAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    void keepsNormalAndDirectPublicationLifecyclesSeparate() {
        assertThatThrownBy(() -> draft().publish(PUBLISHED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only approved posts can be published");
        assertThatThrownBy(() -> approvedPost().publishDirect(PUBLISHED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only draft posts can be directly published");

        PostEntity approvedPost = approvedPost();
        approvedPost.publish(PUBLISHED_AT);

        assertThat(approvedPost.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void rejectsNullDecisionAndMutationInstants() {
        assertThatThrownBy(() -> pendingReview().applyReviewDecision(null, REVIEWED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Review decision is required");
        assertThatThrownBy(() -> draft().submitForReview(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Mutation instant is required");
        assertThatThrownBy(() -> pendingReview().applyReviewDecision(ReviewDecision.APPROVED, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Mutation instant is required");
        assertThatThrownBy(() -> approvedPost().publish(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Mutation instant is required");
        assertThatThrownBy(() -> draft().publishDirect(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Mutation instant is required");
    }

    @Test
    void lifecycleMutationsLeaveRichEditorFieldsUntouched() {
        PostEntity post = draft();
        Long authorUserId = post.getAuthorUserId();
        String title = post.getTitle();
        String slug = post.getSlug();
        String excerpt = post.getExcerpt();
        Map<String, Object> contentJson = post.getContentJson();
        PostVisibility visibility = post.getVisibility();
        Long categoryId = post.getCategoryId();
        Instant createdAt = post.getCreatedAt();

        post.submitForReview(SUBMITTED_AT);
        post.applyReviewDecision(ReviewDecision.APPROVED, REVIEWED_AT);
        post.publish(PUBLISHED_AT);

        assertThat(post.getAuthorUserId()).isEqualTo(authorUserId);
        assertThat(post.getTitle()).isEqualTo(title);
        assertThat(post.getSlug()).isEqualTo(slug);
        assertThat(post.getExcerpt()).isEqualTo(excerpt);
        assertThat(post.getContentJson()).isSameAs(contentJson);
        assertThat(post.getVisibility()).isEqualTo(visibility);
        assertThat(post.getCategoryId()).isEqualTo(categoryId);
        assertThat(post.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void directPublishLeavesRichEditorFieldsUntouched() throws Exception {
        PostEntity post = draft();
        setField(post, "projectId", 11L);
        setField(post, "contentHtml", "<p>Original HTML</p>");
        setField(post, "coverFileId", 12L);
        Long authorUserId = post.getAuthorUserId();
        Long projectId = post.getProjectId();
        String title = post.getTitle();
        String slug = post.getSlug();
        String excerpt = post.getExcerpt();
        Map<String, Object> contentJson = post.getContentJson();
        String contentHtml = post.getContentHtml();
        Long coverFileId = post.getCoverFileId();
        PostVisibility visibility = post.getVisibility();
        Long categoryId = post.getCategoryId();
        Instant createdAt = post.getCreatedAt();

        post.publishDirect(PUBLISHED_AT);

        assertThat(post.getAuthorUserId()).isEqualTo(authorUserId);
        assertThat(post.getProjectId()).isEqualTo(projectId);
        assertThat(post.getTitle()).isEqualTo(title);
        assertThat(post.getSlug()).isEqualTo(slug);
        assertThat(post.getExcerpt()).isEqualTo(excerpt);
        assertThat(post.getContentJson()).isSameAs(contentJson);
        assertThat(post.getContentHtml()).isEqualTo(contentHtml);
        assertThat(post.getCoverFileId()).isEqualTo(coverFileId);
        assertThat(post.getVisibility()).isEqualTo(visibility);
        assertThat(post.getCategoryId()).isEqualTo(categoryId);
        assertThat(post.getCreatedAt()).isEqualTo(createdAt);
        assertThat(post.getDeletedAt()).isNull();
    }

    private static Stream<PostStatus> nonDraftStatuses() {
        return Stream.of(
                PostStatus.PENDING_REVIEW,
                PostStatus.REVISION_REQUIRED,
                PostStatus.APPROVED,
                PostStatus.REJECTED,
                PostStatus.PUBLISHED
        );
    }

    private static Stream<Arguments> reviewDecisionAndInvalidSourceStatuses() {
        return Stream.of(ReviewDecision.values())
                .flatMap(decision -> Stream.of(
                                PostStatus.DRAFT,
                                PostStatus.REVISION_REQUIRED,
                                PostStatus.APPROVED,
                                PostStatus.REJECTED,
                                PostStatus.PUBLISHED
                        )
                        .map(status -> Arguments.of(decision, status)));
    }

    private static Stream<PostStatus> nonApprovedStatuses() {
        return Stream.of(
                PostStatus.DRAFT,
                PostStatus.PENDING_REVIEW,
                PostStatus.REVISION_REQUIRED,
                PostStatus.REJECTED,
                PostStatus.PUBLISHED
        );
    }

    private static PostEntity pendingReview() {
        PostEntity post = draft();
        post.submitForReview(SUBMITTED_AT);
        return post;
    }

    private static PostEntity approvedPost() {
        PostEntity post = pendingReview();
        post.applyReviewDecision(ReviewDecision.APPROVED, REVIEWED_AT);
        return post;
    }

    private static PostEntity draft() {
        return PostEntity.createDraft(
                10L,
                "Original title",
                "original-title",
                "Original excerpt",
                Map.of("type", "doc", "content", "original"),
                PostVisibility.LAB,
                7L,
                CREATED_AT
        );
    }

    private static PostEntity draftWithStatus(PostStatus status) throws Exception {
        PostEntity post = draft();
        setField(post, "status", status);
        return post;
    }

    private static void setField(PostEntity post, String fieldName, Object value) throws Exception {
        Field field = PostEntity.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(post, value);
    }
}
