package com.smartlab.dto.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.smartlab.enums.PostVisibility;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UpdatePostRequestValidationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsEmptyPatch() throws JsonProcessingException {
        assertInvalid(read("{}"));
    }

    @Test
    void distinguishesAbsentTitle() throws JsonProcessingException {
        UpdatePostRequest request = read("{\"excerpt\":null}");

        assertThat(request.hasTitle()).isFalse();
        assertValid(request);
    }

    @Test
    void rejectsExplicitNullTitle() throws JsonProcessingException {
        UpdatePostRequest request = read("{\"title\":null}");

        assertThat(request.hasTitle()).isTrue();
        assertInvalid(request);
    }

    @Test
    void acceptsPresentNonblankTitle() throws JsonProcessingException {
        UpdatePostRequest request = read("{\"title\":\"Updated title\"}");

        assertThat(request.hasTitle()).isTrue();
        assertThat(request.getTitle()).isEqualTo("Updated title");
        assertValid(request);
    }

    @Test
    void rejectsBlankTitle() throws JsonProcessingException {
        assertInvalid(read("{\"title\":\" \\t \"}"));
    }

    @Test
    void acceptsTitleWithExactlyTwoHundredFiftyCharacters() throws JsonProcessingException {
        assertValid(read("{\"title\":\"" + "T".repeat(250) + "\"}"));
    }

    @Test
    void rejectsTitleWithTwoHundredFiftyOneCharacters() throws JsonProcessingException {
        assertInvalid(read("{\"title\":\"" + "T".repeat(251) + "\"}"));
    }

    @Test
    void distinguishesAbsentExcerpt() throws JsonProcessingException {
        UpdatePostRequest request = read("{\"title\":\"Title\"}");

        assertThat(request.hasExcerpt()).isFalse();
        assertValid(request);
    }

    @Test
    void distinguishesExplicitNullExcerpt() throws JsonProcessingException {
        UpdatePostRequest request = read("{\"excerpt\":null}");

        assertThat(request.hasExcerpt()).isTrue();
        assertThat(request.getExcerpt()).isNull();
        assertValid(request);
    }

    @Test
    void acceptsBlankExcerpt() throws JsonProcessingException {
        assertValid(read("{\"excerpt\":\" \\t \"}"));
    }

    @Test
    void acceptsExcerptWithExactlyFiveHundredCharacters() throws JsonProcessingException {
        assertValid(read("{\"excerpt\":\"" + "E".repeat(500) + "\"}"));
    }

    @Test
    void rejectsExcerptWithFiveHundredOneCharacters() throws JsonProcessingException {
        assertInvalid(read("{\"excerpt\":\"" + "E".repeat(501) + "\"}"));
    }

    @Test
    void distinguishesAbsentContentJson() throws JsonProcessingException {
        UpdatePostRequest request = read("{\"title\":\"Title\"}");

        assertThat(request.hasContentJson()).isFalse();
        assertValid(request);
    }

    @Test
    void distinguishesExplicitNullContentJson() throws JsonProcessingException {
        UpdatePostRequest request = read("{\"contentJson\":null}");

        assertThat(request.hasContentJson()).isTrue();
        assertThat(request.getContentJson()).isNull();
        assertValid(request);
    }

    @Test
    void acceptsContentJsonObject() throws JsonProcessingException {
        assertValid(read("{\"contentJson\":{\"type\":\"doc\"}}"));
    }

    @Test
    void rejectsContentJsonArray() throws JsonProcessingException {
        assertInvalid(read("{\"contentJson\":[]}"));
    }

    @Test
    void rejectsContentJsonString() throws JsonProcessingException {
        assertInvalid(read("{\"contentJson\":\"content\"}"));
    }

    @Test
    void rejectsContentJsonNumber() throws JsonProcessingException {
        assertInvalid(read("{\"contentJson\":1}"));
    }

    @Test
    void rejectsContentJsonBoolean() throws JsonProcessingException {
        assertInvalid(read("{\"contentJson\":true}"));
    }

    @Test
    void distinguishesAbsentVisibility() throws JsonProcessingException {
        UpdatePostRequest request = read("{\"title\":\"Title\"}");

        assertThat(request.hasVisibility()).isFalse();
        assertValid(request);
    }

    @Test
    void rejectsExplicitNullVisibility() throws JsonProcessingException {
        UpdatePostRequest request = read("{\"visibility\":null}");

        assertThat(request.hasVisibility()).isTrue();
        assertInvalid(request);
    }

    @Test
    void acceptsPublicVisibility() throws JsonProcessingException {
        UpdatePostRequest request = read("{\"visibility\":\"PUBLIC\"}");

        assertThat(request.getVisibility()).isEqualTo(PostVisibility.PUBLIC);
        assertValid(request);
    }

    @Test
    void acceptsLabVisibility() throws JsonProcessingException {
        UpdatePostRequest request = read("{\"visibility\":\"LAB\"}");

        assertThat(request.getVisibility()).isEqualTo(PostVisibility.LAB);
        assertValid(request);
    }

    @Test
    void rejectsProjectVisibility() throws JsonProcessingException {
        assertInvalid(read("{\"visibility\":\"PROJECT\"}"));
    }

    @Test
    void distinguishesAbsentCategoryId() throws JsonProcessingException {
        UpdatePostRequest request = read("{\"title\":\"Title\"}");

        assertThat(request.hasCategoryId()).isFalse();
        assertValid(request);
    }

    @Test
    void distinguishesExplicitNullCategoryId() throws JsonProcessingException {
        UpdatePostRequest request = read("{\"categoryId\":null}");

        assertThat(request.hasCategoryId()).isTrue();
        assertThat(request.getCategoryId()).isNull();
        assertValid(request);
    }

    @Test
    void acceptsPositiveCategoryId() throws JsonProcessingException {
        assertValid(read("{\"categoryId\":1}"));
    }

    @Test
    void rejectsZeroCategoryId() throws JsonProcessingException {
        assertInvalid(read("{\"categoryId\":0}"));
    }

    @Test
    void rejectsNegativeCategoryId() throws JsonProcessingException {
        assertInvalid(read("{\"categoryId\":-1}"));
    }

    @Test
    void exposesOnlyApprovedMutableFieldsToJackson() {
        Set<String> bindableFields = OBJECT_MAPPER.getDeserializationConfig()
                .introspect(OBJECT_MAPPER.constructType(UpdatePostRequest.class))
                .findProperties()
                .stream()
                .filter(BeanPropertyDefinition::couldDeserialize)
                .map(BeanPropertyDefinition::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(bindableFields).containsExactlyInAnyOrder(
                "title",
                "excerpt",
                "contentJson",
                "visibility",
                "categoryId"
        );
    }

    private static UpdatePostRequest read(String json) throws JsonProcessingException {
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
