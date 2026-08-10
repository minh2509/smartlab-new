package com.smartlab.dto.request;

import com.smartlab.enums.ReviewDecision;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewPostRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void reviewDecisionContainsExactlyTheApprovedValues() {
        assertThat(ReviewDecision.values()).containsExactlyInAnyOrder(
                ReviewDecision.APPROVED,
                ReviewDecision.REVISION_REQUIRED,
                ReviewDecision.REJECTED
        );
    }

    @Test
    void rejectsNullDecision() {
        assertInvalidFor(request(null, null), "decision");
    }

    @Test
    void acceptsApprovedReasonsAllowedByTheContract() {
        assertValid(request(ReviewDecision.APPROVED, null));
        assertValid(request(ReviewDecision.APPROVED, ""));
        assertValid(request(ReviewDecision.APPROVED, " \t "));
        assertValid(request(ReviewDecision.APPROVED, "Looks good"));
        assertValid(request(ReviewDecision.APPROVED, "a".repeat(1_000)));
    }

    @Test
    void rejectsApprovedReasonLongerThanOneThousandCharacters() {
        assertInvalidFor(request(ReviewDecision.APPROVED, "a".repeat(1_001)), "reason");
    }

    @Test
    void acceptsRevisionRequiredReasonsAllowedByTheContract() {
        assertValid(request(ReviewDecision.REVISION_REQUIRED, null));
        assertValid(request(ReviewDecision.REVISION_REQUIRED, ""));
        assertValid(request(ReviewDecision.REVISION_REQUIRED, " \t "));
        assertValid(request(ReviewDecision.REVISION_REQUIRED, "Please clarify the conclusion"));
        assertValid(request(ReviewDecision.REVISION_REQUIRED, "a".repeat(1_000)));
    }

    @Test
    void rejectsRevisionRequiredReasonLongerThanOneThousandCharacters() {
        assertInvalidFor(request(ReviewDecision.REVISION_REQUIRED, "a".repeat(1_001)), "reason");
    }

    @Test
    void rejectsRejectedDecisionWithoutANonblankReason() {
        assertInvalidFor(request(ReviewDecision.REJECTED, null), "rejectedReasonValid");
        assertInvalidFor(request(ReviewDecision.REJECTED, ""), "rejectedReasonValid");
        assertInvalidFor(request(ReviewDecision.REJECTED, " \t\n "), "rejectedReasonValid");
    }

    @Test
    void acceptsRejectedDecisionWithANonblankReasonUpToOneThousandCharacters() {
        assertValid(request(ReviewDecision.REJECTED, "The post needs substantial changes"));
        assertValid(request(ReviewDecision.REJECTED, "a".repeat(1_000)));
    }

    @Test
    void rejectsRejectedReasonLongerThanOneThousandCharacters() {
        assertInvalidFor(request(ReviewDecision.REJECTED, "a".repeat(1_001)), "reason");
    }

    @Test
    void exposesOnlyDecisionAndReasonWithoutCallerControlledIdentity() {
        assertThat(Arrays.stream(ReviewPostRequest.class.getDeclaredFields())
                .map(field -> field.getName())
                .toList())
                .containsExactlyInAnyOrder("decision", "reason");
    }

    private static ReviewPostRequest request(ReviewDecision decision, String reason) {
        return new ReviewPostRequest(decision, reason);
    }

    private static void assertValid(ReviewPostRequest request) {
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    private static void assertInvalidFor(ReviewPostRequest request, String property) {
        Set<ConstraintViolation<ReviewPostRequest>> violations = VALIDATOR.validate(request);
        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(property);
    }
}
