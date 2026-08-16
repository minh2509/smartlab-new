package com.smartlab.dto.request;

import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreatePostRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void postStatusContainsExactlyTheApprovedValues() {
        assertThat(PostStatus.values()).containsExactlyInAnyOrder(
                PostStatus.DRAFT,
                PostStatus.PENDING_REVIEW,
                PostStatus.REVISION_REQUIRED,
                PostStatus.APPROVED,
                PostStatus.PUBLISHED,
                PostStatus.REJECTED
        );
    }

    @Test
    void postStatusDoesNotContainArchived() {
        assertThat(Arrays.stream(PostStatus.values()).map(Enum::name)).doesNotContain("ARCHIVED");
    }

    @Test
    void postVisibilityContainsExactlyTheApprovedValues() {
        assertThat(PostVisibility.values()).containsExactlyInAnyOrder(
                PostVisibility.PUBLIC,
                PostVisibility.LAB,
                PostVisibility.PROJECT
        );
    }

    @Test
    void rejectsNullTitle() {
        assertInvalid(request(null, null, null, null, null));
    }

    @Test
    void rejectsEmptyTitle() {
        assertInvalid(request("", null, null, null, null));
    }

    @Test
    void rejectsWhitespaceOnlyTitle() {
        assertInvalid(request(" \t ", null, null, null, null));
    }

    @Test
    void acceptsTitleWithExactlyTwoHundredFiftyCharacters() {
        assertValid(request("T".repeat(250), null, null, null, null));
    }

    @Test
    void rejectsTitleWithTwoHundredFiftyOneCharacters() {
        assertInvalid(request("T".repeat(251), null, null, null, null));
    }

    @Test
    void acceptsNullExcerpt() {
        assertValid(request("Title", null, null, null, null));
    }

    @Test
    void acceptsEmptyExcerpt() {
        assertValid(request("Title", "", null, null, null));
    }

    @Test
    void acceptsWhitespaceOnlyExcerpt() {
        assertValid(request("Title", " \t ", null, null, null));
    }

    @Test
    void acceptsExcerptWithExactlyFiveHundredCharacters() {
        assertValid(request("Title", "E".repeat(500), null, null, null));
    }

    @Test
    void rejectsExcerptWithFiveHundredOneCharacters() {
        assertInvalid(request("Title", "E".repeat(501), null, null, null));
    }

    @Test
    void acceptsNullContentJson() {
        assertValid(request("Title", null, null, null, null));
    }

    @Test
    void acceptsAbsentContentJson() {
        assertValid(OBJECT_MAPPER.readValue("{\"title\":\"Title\"}", CreatePostRequest.class));
    }

    @Test
    void acceptsEmptyContentJsonObject() {
        assertValid(request("Title", null, Map.of(), null, null));
    }

    @Test
    void acceptsNormalContentJsonObject() {
        assertValid(request("Title", null, Map.of("type", "doc", "nested", Map.of("enabled", true)), null, null));
    }

    @Test
    void rejectsContentJsonArray() {
        assertThatThrownBy(() -> OBJECT_MAPPER.readValue(
                "{\"title\":\"Title\",\"contentJson\":[]}", CreatePostRequest.class))
                .isInstanceOf(tools.jackson.core.JacksonException.class);
    }

    @Test
    void rejectsContentJsonString() {
        assertThatThrownBy(() -> OBJECT_MAPPER.readValue(
                "{\"title\":\"Title\",\"contentJson\":\"content\"}", CreatePostRequest.class))
                .isInstanceOf(tools.jackson.core.JacksonException.class);
    }

    @Test
    void rejectsContentJsonNumber() {
        assertThatThrownBy(() -> OBJECT_MAPPER.readValue(
                "{\"title\":\"Title\",\"contentJson\":1}", CreatePostRequest.class))
                .isInstanceOf(tools.jackson.core.JacksonException.class);
    }

    @Test
    void rejectsContentJsonBoolean() {
        assertThatThrownBy(() -> OBJECT_MAPPER.readValue(
                "{\"title\":\"Title\",\"contentJson\":true}", CreatePostRequest.class))
                .isInstanceOf(tools.jackson.core.JacksonException.class);
    }

    @Test
    void acceptsNullVisibility() {
        assertValid(request("Title", null, null, null, null));
    }

    @Test
    void acceptsLabVisibility() {
        assertValid(request("Title", null, null, PostVisibility.LAB, null));
    }

    @Test
    void acceptsPublicVisibility() {
        assertValid(request("Title", null, null, PostVisibility.PUBLIC, null));
    }

    @Test
    void acceptsProjectVisibilityWithPositiveProjectId() {
        assertValid(request("Title", null, null, PostVisibility.PROJECT, null, 1L));
    }

    @Test
    void rejectsProjectVisibilityWithoutProjectId() {
        assertInvalid(request("Title", null, null, PostVisibility.PROJECT, null, null));
    }

    @Test
    void rejectsProjectIdForPublicOrLabVisibility() {
        assertInvalid(request("Title", null, null, PostVisibility.PUBLIC, null, 1L));
        assertInvalid(request("Title", null, null, PostVisibility.LAB, null, 1L));
    }

    @Test
    void acceptsNullCategoryId() {
        assertValid(request("Title", null, null, null, null));
    }

    @Test
    void acceptsPositiveCategoryId() {
        assertValid(request("Title", null, null, null, 1L));
    }

    @Test
    void rejectsZeroCategoryId() {
        assertInvalid(request("Title", null, null, null, 0L));
    }

    @Test
    void rejectsNegativeCategoryId() {
        assertInvalid(request("Title", null, null, null, -1L));
    }

    @Test
    void exposesOnlyApprovedCallerOwnedFields() {
        assertThat(Arrays.stream(CreatePostRequest.class.getDeclaredFields())
                .map(field -> field.getName())
                .toList())
                .containsExactlyInAnyOrder("title", "excerpt", "contentJson", "visibility", "categoryId", "projectId");
    }

    private static CreatePostRequest request(
            String title,
            String excerpt,
            Map<String, Object> contentJson,
            PostVisibility visibility,
            Long categoryId
    ) {
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle(title);
        request.setExcerpt(excerpt);
        request.setContentJson(contentJson);
        request.setVisibility(visibility);
        request.setCategoryId(categoryId);
        return request;
    }

    private static CreatePostRequest request(
            String title,
            String excerpt,
            Map<String, Object> contentJson,
            PostVisibility visibility,
            Long categoryId,
            Long projectId
    ) {
        CreatePostRequest request = request(title, excerpt, contentJson, visibility, categoryId);
        request.setProjectId(projectId);
        return request;
    }

    private static void assertValid(CreatePostRequest request) {
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    private static void assertInvalid(CreatePostRequest request) {
        Set<ConstraintViolation<CreatePostRequest>> violations = VALIDATOR.validate(request);
        assertThat(violations).isNotEmpty();
    }
}
