package com.smartlab.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreatePostRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

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
    void acceptsEmptyContentJsonObject() {
        assertValid(request("Title", null, JsonNodeFactory.instance.objectNode(), null, null));
    }

    @Test
    void acceptsNormalContentJsonObject() {
        assertValid(request("Title", null, JsonNodeFactory.instance.objectNode().put("type", "doc"), null, null));
    }

    @Test
    void rejectsContentJsonArray() {
        assertInvalid(request("Title", null, JsonNodeFactory.instance.arrayNode(), null, null));
    }

    @Test
    void rejectsContentJsonString() {
        assertInvalid(request("Title", null, JsonNodeFactory.instance.textNode("content"), null, null));
    }

    @Test
    void rejectsContentJsonNumber() {
        assertInvalid(request("Title", null, JsonNodeFactory.instance.numberNode(1), null, null));
    }

    @Test
    void rejectsContentJsonBoolean() {
        assertInvalid(request("Title", null, JsonNodeFactory.instance.booleanNode(true), null, null));
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
    void rejectsProjectVisibilityForCreateRequest() {
        assertInvalid(request("Title", null, null, PostVisibility.PROJECT, null));
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
                .containsExactlyInAnyOrder("title", "excerpt", "contentJson", "visibility", "categoryId");
    }

    private static CreatePostRequest request(
            String title,
            String excerpt,
            JsonNode contentJson,
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

    private static void assertValid(CreatePostRequest request) {
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    private static void assertInvalid(CreatePostRequest request) {
        Set<ConstraintViolation<CreatePostRequest>> violations = VALIDATOR.validate(request);
        assertThat(violations).isNotEmpty();
    }
}
