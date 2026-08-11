package com.smartlab.dto.request;

import com.smartlab.enums.PostVisibility;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdatePostRequestValidationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsEmptyPatch() throws JacksonException {
        assertInvalid(read("{}"));
    }

    @Test
    void distinguishesAbsentTitle() throws JacksonException {
        UpdatePostRequest request = read("{\"excerpt\":null}");

        assertThat(request.hasTitle()).isFalse();
        assertValid(request);
    }

    @Test
    void rejectsExplicitNullTitle() throws JacksonException {
        UpdatePostRequest request = read("{\"title\":null}");

        assertThat(request.hasTitle()).isTrue();
        assertInvalid(request);
    }

    @Test
    void acceptsPresentNonblankTitle() throws JacksonException {
        UpdatePostRequest request = read("{\"title\":\"Updated title\"}");

        assertThat(request.hasTitle()).isTrue();
        assertThat(request.getTitle()).isEqualTo("Updated title");
        assertValid(request);
    }

    @Test
    void rejectsBlankTitle() throws JacksonException {
        assertInvalid(read("{\"title\":\" \\t \"}"));
    }

    @Test
    void acceptsTitleWithExactlyTwoHundredFiftyCharacters() throws JacksonException {
        assertValid(read("{\"title\":\"" + "T".repeat(250) + "\"}"));
    }

    @Test
    void rejectsTitleWithTwoHundredFiftyOneCharacters() throws JacksonException {
        assertInvalid(read("{\"title\":\"" + "T".repeat(251) + "\"}"));
    }

    @Test
    void distinguishesAbsentExcerpt() throws JacksonException {
        UpdatePostRequest request = read("{\"title\":\"Title\"}");

        assertThat(request.hasExcerpt()).isFalse();
        assertValid(request);
    }

    @Test
    void distinguishesExplicitNullExcerpt() throws JacksonException {
        UpdatePostRequest request = read("{\"excerpt\":null}");

        assertThat(request.hasExcerpt()).isTrue();
        assertThat(request.getExcerpt()).isNull();
        assertValid(request);
    }

    @Test
    void acceptsBlankExcerpt() throws JacksonException {
        assertValid(read("{\"excerpt\":\" \\t \"}"));
    }

    @Test
    void acceptsExcerptWithExactlyFiveHundredCharacters() throws JacksonException {
        assertValid(read("{\"excerpt\":\"" + "E".repeat(500) + "\"}"));
    }

    @Test
    void rejectsExcerptWithFiveHundredOneCharacters() throws JacksonException {
        assertInvalid(read("{\"excerpt\":\"" + "E".repeat(501) + "\"}"));
    }

    @Test
    void distinguishesAbsentContentJson() throws JacksonException {
        UpdatePostRequest request = read("{\"title\":\"Title\"}");

        assertThat(request.hasContentJson()).isFalse();
        assertValid(request);
    }

    @Test
    void distinguishesExplicitNullContentJson() throws JacksonException {
        UpdatePostRequest request = read("{\"contentJson\":null}");

        assertThat(request.hasContentJson()).isTrue();
        assertThat(request.getContentJson()).isNull();
        assertValid(request);
    }

    @Test
    void acceptsContentJsonObject() throws JacksonException {
        UpdatePostRequest request = read("{\"contentJson\":{\"type\":\"doc\",\"nested\":{\"enabled\":true}}}");

        assertThat(request.hasContentJson()).isTrue();
        assertThat(request.getContentJson()).containsEntry("type", "doc");
        assertValid(request);
    }

    @Test
    void rejectsContentJsonArray() {
        assertThatThrownBy(() -> read("{\"contentJson\":[]}"))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void rejectsContentJsonString() {
        assertThatThrownBy(() -> read("{\"contentJson\":\"content\"}"))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void rejectsContentJsonNumber() {
        assertThatThrownBy(() -> read("{\"contentJson\":1}"))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void rejectsContentJsonBoolean() {
        assertThatThrownBy(() -> read("{\"contentJson\":true}"))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void distinguishesAbsentVisibility() throws JacksonException {
        UpdatePostRequest request = read("{\"title\":\"Title\"}");

        assertThat(request.hasVisibility()).isFalse();
        assertValid(request);
    }

    @Test
    void rejectsExplicitNullVisibility() throws JacksonException {
        UpdatePostRequest request = read("{\"visibility\":null}");

        assertThat(request.hasVisibility()).isTrue();
        assertInvalid(request);
    }

    @Test
    void acceptsPublicVisibility() throws JacksonException {
        UpdatePostRequest request = read("{\"visibility\":\"PUBLIC\"}");

        assertThat(request.getVisibility()).isEqualTo(PostVisibility.PUBLIC);
        assertValid(request);
    }

    @Test
    void acceptsLabVisibility() throws JacksonException {
        UpdatePostRequest request = read("{\"visibility\":\"LAB\"}");

        assertThat(request.getVisibility()).isEqualTo(PostVisibility.LAB);
        assertValid(request);
    }

    @Test
    void acceptsProjectVisibility() throws JacksonException {
        assertValid(read("{\"visibility\":\"PROJECT\"}"));
    }

    @Test
    void distinguishesAbsentCategoryId() throws JacksonException {
        UpdatePostRequest request = read("{\"title\":\"Title\"}");

        assertThat(request.hasCategoryId()).isFalse();
        assertValid(request);
    }

    @Test
    void distinguishesExplicitNullCategoryId() throws JacksonException {
        UpdatePostRequest request = read("{\"categoryId\":null}");

        assertThat(request.hasCategoryId()).isTrue();
        assertThat(request.getCategoryId()).isNull();
        assertValid(request);
    }

    @Test
    void acceptsPositiveCategoryId() throws JacksonException {
        assertValid(read("{\"categoryId\":1}"));
    }

    @Test
    void rejectsZeroCategoryId() throws JacksonException {
        assertInvalid(read("{\"categoryId\":0}"));
    }

    @Test
    void rejectsNegativeCategoryId() throws JacksonException {
        assertInvalid(read("{\"categoryId\":-1}"));
    }

    @Test
    void tracksProjectIdPresenceAndValidatesPositiveValues() throws JacksonException {
        UpdatePostRequest absent = read("{\"title\":\"Title\"}");
        UpdatePostRequest explicitNull = read("{\"projectId\":null}");

        assertThat(absent.hasProjectId()).isFalse();
        assertThat(explicitNull.hasProjectId()).isTrue();
        assertValid(explicitNull);
        assertValid(read("{\"projectId\":1}"));
        assertInvalid(read("{\"projectId\":0}"));
        assertInvalid(read("{\"projectId\":-1}"));
    }

    @Test
    void exposesOnlyApprovedMutableFieldsToJackson() {
        Set<String> bindableFields = Arrays.stream(UpdatePostRequest.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(JsonSetter.class))
                .map(method -> method.getAnnotation(JsonSetter.class).value())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(bindableFields).containsExactlyInAnyOrder(
                "title",
                "excerpt",
                "contentJson",
                "visibility",
                "categoryId",
                "projectId"
        );
    }

    private static UpdatePostRequest read(String json) throws JacksonException {
        return OBJECT_MAPPER.readValue(json, UpdatePostRequest.class);
    }

    private static void assertValid(UpdatePostRequest request) {
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    private static void assertInvalid(UpdatePostRequest request) {
        Set<ConstraintViolation<UpdatePostRequest>> violations = VALIDATOR.validate(request);
        assertThat(violations).isNotEmpty();
    }
}
