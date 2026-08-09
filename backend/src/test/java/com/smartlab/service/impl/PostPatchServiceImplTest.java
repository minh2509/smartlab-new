package com.smartlab.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.smartlab.dto.request.UpdatePostRequest;
import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.entity.ContentCategoryEntity;
import com.smartlab.entity.PostEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import com.smartlab.repo.ContentCategoryRepository;
import com.smartlab.repo.PostRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.PostService;
import com.smartlab.service.PostSlugGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
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
class PostPatchServiceImplTest {

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
    private PostSlugGenerator postSlugGenerator;

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostServiceImpl(userRepository, contentCategoryRepository, postRepository, postSlugGenerator);
    }

    @AfterEach
    void patchNeverGeneratesSlugOrRequiresExplicitSave() {
        verifyNoInteractions(postSlugGenerator);
        verify(postRepository, never()).save(any(PostEntity.class));
    }

    @Test
    void trustedActiveOwnerUpdatesDraftTitleThroughLockedLookupWithoutChangingSlug() {
        PostEntity post = draft();
        UpdatePostRequest request = new UpdatePostRequest();
        request.setTitle("Updated exactly");
        activeOwner();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));

        PostDetailResponse response = postService.updatePost(OWNER_EMAIL, POST_ID, request);

        verify(userRepository).findByEmail(OWNER_EMAIL);
        verify(postRepository).findActiveByIdForUpdate(POST_ID);
        assertThat(post.getTitle()).isEqualTo("Updated exactly");
        assertThat(post.getSlug()).isEqualTo("immutable-slug");
        assertThat(response.getTitle()).isEqualTo("Updated exactly");
        assertThat(response.getSlug()).isEqualTo("immutable-slug");
    }

    @Test
    void missingTrustedUserReturnsUnauthorizedBeforePostLookup() {
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.UNAUTHORIZED,
                () -> postService.updatePost(OWNER_EMAIL, POST_ID, titleRequest("Updated")));

        verify(postRepository, never()).findActiveByIdForUpdate(any());
        verifyNoInteractions(contentCategoryRepository);
    }

    @Test
    void inactiveTrustedUserReturnsUnauthorizedBeforePostLookup() {
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(user(OWNER_ID, false)));

        assertStatus(HttpStatus.UNAUTHORIZED,
                () -> postService.updatePost(OWNER_EMAIL, POST_ID, titleRequest("Updated")));

        verify(postRepository, never()).findActiveByIdForUpdate(any());
        verifyNoInteractions(contentCategoryRepository);
    }

    @Test
    void missingOrSoftDeletedActivePostReturnsNotFound() {
        activeOwner();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.NOT_FOUND,
                () -> postService.updatePost(OWNER_EMAIL, POST_ID, titleRequest("Updated")));

        verifyNoInteractions(contentCategoryRepository);
    }

    @Test
    void authorlessActiveDraftReturnsForbiddenWithoutMutation() throws ReflectiveOperationException {
        PostEntity post = draft();
        set(post, "authorUserId", null);
        assertEligibilityFailureDoesNotMutate(post, HttpStatus.FORBIDDEN);
    }

    @Test
    void anotherUsersActiveDraftReturnsForbiddenWithoutMutation() throws ReflectiveOperationException {
        PostEntity post = draft();
        set(post, "authorUserId", 99L);
        assertEligibilityFailureDoesNotMutate(post, HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @EnumSource(value = PostStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void ownedActiveNonDraftReturnsConflictWithoutMutation(PostStatus status) throws ReflectiveOperationException {
        PostEntity post = draft();
        set(post, "status", status);

        assertEligibilityFailureDoesNotMutate(post, HttpStatus.CONFLICT);
    }

    @Test
    void absentFieldsPreserveExcerptContentVisibilityAndCategory() {
        PostEntity post = draft();
        JsonNode originalContent = post.getContentJson();
        Instant previousUpdatedAt = post.getUpdatedAt();
        stubOwnedDraft(post);

        postService.updatePost(OWNER_EMAIL, POST_ID, titleRequest("Only title changes"));

        assertThat(post.getExcerpt()).isEqualTo("Original excerpt");
        assertThat(post.getContentJson()).isSameAs(originalContent);
        assertThat(post.getVisibility()).isEqualTo(PostVisibility.LAB);
        assertThat(post.getCategoryId()).isNull();
        assertThat(post.getUpdatedAt()).isAfter(previousUpdatedAt);
    }

    @Test
    void absentTitlePreservesCurrentTitle() {
        PostEntity post = draft();
        UpdatePostRequest request = new UpdatePostRequest();
        request.setExcerpt("Updated excerpt");
        stubOwnedDraft(post);

        PostDetailResponse response = postService.updatePost(OWNER_EMAIL, POST_ID, request);

        assertThat(post.getTitle()).isEqualTo("Original title");
        assertThat(response.getTitle()).isEqualTo("Original title");
    }

    @ParameterizedTest
    @MethodSource("excerptValues")
    void presentExcerptPersistsExactValueIncludingNullAndBlank(String suppliedExcerpt) {
        PostEntity post = draft();
        UpdatePostRequest request = new UpdatePostRequest();
        request.setExcerpt(suppliedExcerpt);
        stubOwnedDraft(post);

        PostDetailResponse response = postService.updatePost(OWNER_EMAIL, POST_ID, request);

        assertThat(post.getExcerpt()).isEqualTo(suppliedExcerpt);
        assertThat(response.getExcerpt()).isEqualTo(suppliedExcerpt);
    }

    @Test
    void explicitNullContentCreatesNewEmptyObjectWithoutMutatingRequest() {
        PostEntity post = draft();
        JsonNode originalContent = post.getContentJson();
        UpdatePostRequest request = new UpdatePostRequest();
        request.setContentJson(null);
        stubOwnedDraft(post);

        PostDetailResponse response = postService.updatePost(OWNER_EMAIL, POST_ID, request);

        assertThat((Object) post.getContentJson()).isNotNull().isNotSameAs(originalContent);
        assertThat(post.getContentJson().isObject()).isTrue();
        assertThat(post.getContentJson().size()).isZero();
        assertThat(response.getContentJson()).isSameAs(post.getContentJson());
        assertThat(request.hasContentJson()).isTrue();
        assertThat(request.getContentJson()).isNull();
    }

    @Test
    void suppliedContentObjectReplacesExistingContentWithoutConversion() {
        PostEntity post = draft();
        JsonNode replacement = JsonNodeFactory.instance.objectNode().put("type", "replacement");
        UpdatePostRequest request = new UpdatePostRequest();
        request.setContentJson(replacement);
        stubOwnedDraft(post);

        PostDetailResponse response = postService.updatePost(OWNER_EMAIL, POST_ID, request);

        assertThat(post.getContentJson()).isSameAs(replacement);
        assertThat(response.getContentJson()).isSameAs(replacement);
        assertThat(request.getContentJson()).isSameAs(replacement);
    }

    @ParameterizedTest
    @EnumSource(value = PostVisibility.class, names = {"PUBLIC", "LAB"})
    void presentSupportedVisibilityUpdatesExactly(PostVisibility visibility) {
        PostEntity post = draft();
        UpdatePostRequest request = new UpdatePostRequest();
        request.setVisibility(visibility);
        stubOwnedDraft(post);

        PostDetailResponse response = postService.updatePost(OWNER_EMAIL, POST_ID, request);

        assertThat(post.getVisibility()).isEqualTo(visibility);
        assertThat(response.getVisibility()).isEqualTo(visibility);
        assertThat(post.getProjectId()).isNull();
    }

    @Test
    void absentCategoryAllowsAndMapsUnchangedInactiveCategory() throws ReflectiveOperationException {
        PostEntity post = draft();
        set(post, "categoryId", 7L);
        ContentCategoryEntity inactiveCategory = category(7L, "ARCHIVE", "Archive", false);
        stubOwnedDraft(post);
        when(contentCategoryRepository.findById(7L)).thenReturn(Optional.of(inactiveCategory));

        PostDetailResponse response = postService.updatePost(OWNER_EMAIL, POST_ID, titleRequest("Updated"));

        assertThat(post.getCategoryId()).isEqualTo(7L);
        assertThat(response.getCategory()).extracting("id", "code", "name")
                .containsExactly(7L, "ARCHIVE", "Archive");
        verify(contentCategoryRepository, never()).findByIdAndIsActiveTrue(any());
    }

    @Test
    void absentCategoryWithMissingOldMetadataStillSucceedsWithNullCategory() throws ReflectiveOperationException {
        PostEntity post = draft();
        set(post, "categoryId", 7L);
        stubOwnedDraft(post);
        when(contentCategoryRepository.findById(7L)).thenReturn(Optional.empty());

        PostDetailResponse response = postService.updatePost(OWNER_EMAIL, POST_ID, titleRequest("Updated"));

        assertThat(post.getCategoryId()).isEqualTo(7L);
        assertThat(response.getCategory()).isNull();
        verify(contentCategoryRepository, never()).findByIdAndIsActiveTrue(any());
    }

    @Test
    void explicitNullCategoryClearsWithoutCategoryLookup() throws ReflectiveOperationException {
        PostEntity post = draft();
        set(post, "categoryId", 7L);
        UpdatePostRequest request = new UpdatePostRequest();
        request.setCategoryId(null);
        stubOwnedDraft(post);

        PostDetailResponse response = postService.updatePost(OWNER_EMAIL, POST_ID, request);

        assertThat(post.getCategoryId()).isNull();
        assertThat(response.getCategory()).isNull();
        verifyNoInteractions(contentCategoryRepository);
    }

    @Test
    void activeSuppliedCategoryIsPersistedAndReusedForResponse() {
        PostEntity post = draft();
        ContentCategoryEntity category = category(9L, "NEWS", "News", true);
        UpdatePostRequest request = new UpdatePostRequest();
        request.setCategoryId(9L);
        stubOwnedDraft(post);
        when(contentCategoryRepository.findByIdAndIsActiveTrue(9L)).thenReturn(Optional.of(category));

        PostDetailResponse response = postService.updatePost(OWNER_EMAIL, POST_ID, request);

        assertThat(post.getCategoryId()).isEqualTo(9L);
        assertThat(response.getCategory()).extracting("id", "code", "name")
                .containsExactly(9L, "NEWS", "News");
        verify(contentCategoryRepository).findByIdAndIsActiveTrue(9L);
        verify(contentCategoryRepository, never()).findById(any());
    }

    @ParameterizedTest(name = "{0} supplied category returns bad request")
    @MethodSource("unavailableCategoryIds")
    void missingOrInactiveSuppliedCategoryReturnsBadRequestWithoutMutation(String scenario, Long categoryId) {
        PostEntity post = draft();
        PostSnapshot before = snapshot(post);
        UpdatePostRequest request = new UpdatePostRequest();
        request.setCategoryId(categoryId);
        stubOwnedDraft(post);
        when(contentCategoryRepository.findByIdAndIsActiveTrue(categoryId)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.BAD_REQUEST,
                () -> postService.updatePost(OWNER_EMAIL, POST_ID, request));

        assertThat(snapshot(post)).isEqualTo(before);
        verify(contentCategoryRepository, never()).findById(any());
    }

    @Test
    void successfulPatchChangesOnlyMutableFieldsAndReturnsApprovedDetailShape() throws ReflectiveOperationException {
        PostEntity post = draft();
        Instant createdAt = post.getCreatedAt();
        Instant previousUpdatedAt = post.getUpdatedAt();
        Instant publishedAt = Instant.parse("2026-08-02T10:00:00Z");
        set(post, "projectId", 51L);
        set(post, "coverFileId", 61L);
        set(post, "contentHtml", "<p>unchanged</p>");
        set(post, "publishedAt", publishedAt);
        JsonNode replacement = JsonNodeFactory.instance.objectNode().put("type", "updated");
        UpdatePostRequest request = new UpdatePostRequest();
        request.setTitle("Updated title");
        request.setExcerpt("  exact excerpt  ");
        request.setContentJson(replacement);
        request.setVisibility(PostVisibility.PUBLIC);
        request.setCategoryId(null);
        stubOwnedDraft(post);

        PostDetailResponse response = postService.updatePost(OWNER_EMAIL, POST_ID, request);

        assertThat(post.getUpdatedAt()).isAfter(previousUpdatedAt);
        assertThat(post.getCreatedAt()).isEqualTo(createdAt);
        assertThat(post.getSlug()).isEqualTo("immutable-slug");
        assertThat(post.getAuthorUserId()).isEqualTo(OWNER_ID);
        assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(post.getProjectId()).isEqualTo(51L);
        assertThat(post.getCoverFileId()).isEqualTo(61L);
        assertThat(post.getContentHtml()).isEqualTo("<p>unchanged</p>");
        assertThat(post.getPublishedAt()).isEqualTo(publishedAt);
        assertThat(post.getDeletedAt()).isNull();
        assertThat(response.getId()).isEqualTo(POST_ID);
        assertThat(response.getTitle()).isEqualTo("Updated title");
        assertThat(response.getSlug()).isEqualTo("immutable-slug");
        assertThat(response.getExcerpt()).isEqualTo("  exact excerpt  ");
        assertThat(response.getContentJson()).isSameAs(replacement);
        assertThat(response.getVisibility()).isEqualTo(PostVisibility.PUBLIC);
        assertThat(response.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(response.getCategory()).isNull();
        assertThat(response.getPublishedAt()).isEqualTo(publishedAt);
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
        assertThat(response.getUpdatedAt()).isEqualTo(post.getUpdatedAt());
        assertThat(Arrays.stream(PostDetailResponse.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("authorUserId", "contentHtml", "projectId", "coverFileId", "deletedAt");
    }

    @Test
    void updatePostUsesOrdinaryWriteTransaction() throws NoSuchMethodException {
        Method method = PostServiceImpl.class.getMethod(
                "updatePost",
                String.class,
                Long.class,
                UpdatePostRequest.class
        );
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    private void activeOwner() {
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(user(OWNER_ID, true)));
    }

    private void stubOwnedDraft(PostEntity post) {
        activeOwner();
        when(postRepository.findActiveByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));
    }

    private void assertEligibilityFailureDoesNotMutate(PostEntity post, HttpStatus expectedStatus) {
        PostSnapshot before = snapshot(post);
        stubOwnedDraft(post);

        assertStatus(expectedStatus,
                () -> postService.updatePost(OWNER_EMAIL, POST_ID, titleRequest("Updated")));

        assertThat(snapshot(post)).isEqualTo(before);
        verifyNoInteractions(contentCategoryRepository);
    }

    private static void assertStatus(HttpStatus expectedStatus, ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(expectedStatus));
    }

    private static Stream<String> excerptValues() {
        return Stream.of(null, " \t ", "  supplied exactly  ");
    }

    private static Stream<Arguments> unavailableCategoryIds() {
        return Stream.of(
                Arguments.of("missing", 98L),
                Arguments.of("inactive", 99L)
        );
    }

    private static UpdatePostRequest titleRequest(String title) {
        UpdatePostRequest request = new UpdatePostRequest();
        request.setTitle(title);
        return request;
    }

    private static UserEntity user(Long id, boolean active) {
        return UserEntity.builder().id(id).email(OWNER_EMAIL).isActive(active).build();
    }

    private static ContentCategoryEntity category(Long id, String code, String name, boolean active) {
        return ContentCategoryEntity.builder()
                .id(id)
                .code(code)
                .name(name)
                .isActive(active)
                .build();
    }

    private static PostEntity draft() {
        try {
            Instant createdAt = Instant.parse("2026-08-01T10:00:00Z");
            PostEntity post = PostEntity.createDraft(
                    OWNER_ID,
                    "Original title",
                    "immutable-slug",
                    "Original excerpt",
                    JsonNodeFactory.instance.objectNode().put("type", "original"),
                    PostVisibility.LAB,
                    null,
                    createdAt
            );
            set(post, "id", POST_ID);
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

    private record PostSnapshot(
            Long authorUserId,
            Long projectId,
            Long categoryId,
            String title,
            String slug,
            String excerpt,
            JsonNode contentJson,
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
