package com.smartlab.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberProfileRequestValidationTest {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void optionalPublicEmailMayBeBlankOrValid() {
        UpdateMemberProfileRequest blank = new UpdateMemberProfileRequest();
        blank.setPublicEmail("");
        UpdateMemberProfileRequest valid = new UpdateMemberProfileRequest();
        valid.setPublicEmail("member@smartlab.test");

        assertThat(VALIDATOR.validate(blank)).isEmpty();
        assertThat(VALIDATOR.validate(valid)).isEmpty();
    }

    @Test
    void optionalPublicEmailRejectsMalformedValue() {
        UpdateMemberProfileRequest request = new UpdateMemberProfileRequest();
        request.setPublicEmail("not-an-email");

        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("publicEmail");
    }
}
