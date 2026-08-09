package com.smartlab.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateContentCategoryRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidCode() {
        assertValid(request("NEWS", "News", null));
    }

    @Test
    void rejectsNullCode() {
        assertInvalid(request(null, "News", null));
    }

    @Test
    void rejectsEmptyCode() {
        assertInvalid(request("", "News", null));
    }

    @Test
    void rejectsWhitespaceOnlyCode() {
        assertInvalid(request(" \t ", "News", null));
    }

    @Test
    void acceptsCodeWithExactlyEightyCharacters() {
        assertValid(request("C".repeat(80), "News", null));
    }

    @Test
    void rejectsCodeWithEightyOneCharacters() {
        assertInvalid(request("C".repeat(81), "News", null));
    }

    @Test
    void acceptsValidName() {
        assertValid(request("NEWS", "News", null));
    }

    @Test
    void rejectsNullName() {
        assertInvalid(request("NEWS", null, null));
    }

    @Test
    void rejectsEmptyName() {
        assertInvalid(request("NEWS", "", null));
    }

    @Test
    void rejectsWhitespaceOnlyName() {
        assertInvalid(request("NEWS", " \t ", null));
    }

    @Test
    void acceptsNameWithExactlyOneHundredFiftyCharacters() {
        assertValid(request("NEWS", "N".repeat(150), null));
    }

    @Test
    void rejectsNameWithOneHundredFiftyOneCharacters() {
        assertInvalid(request("NEWS", "N".repeat(151), null));
    }

    @Test
    void acceptsNullDescription() {
        assertValid(request("NEWS", "News", null));
    }

    @Test
    void acceptsEmptyDescription() {
        assertValid(request("NEWS", "News", ""));
    }

    @Test
    void acceptsBlankDescription() {
        assertValid(request("NEWS", "News", " \t "));
    }

    @Test
    void acceptsNormalDescription() {
        assertValid(request("NEWS", "News", "Updates and announcements"));
    }

    @Test
    void acceptsDescriptionWithExactlyFiveHundredCharacters() {
        assertValid(request("NEWS", "News", "D".repeat(500)));
    }

    @Test
    void rejectsDescriptionWithFiveHundredOneCharacters() {
        assertInvalid(request("NEWS", "News", "D".repeat(501)));
    }

    private static CreateContentCategoryRequest request(String code, String name, String description) {
        CreateContentCategoryRequest request = new CreateContentCategoryRequest();
        request.setCode(code);
        request.setName(name);
        request.setDescription(description);
        return request;
    }

    private static void assertValid(CreateContentCategoryRequest request) {
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    private static void assertInvalid(CreateContentCategoryRequest request) {
        Set<ConstraintViolation<CreateContentCategoryRequest>> violations = VALIDATOR.validate(request);
        assertThat(violations).isNotEmpty();
    }
}
