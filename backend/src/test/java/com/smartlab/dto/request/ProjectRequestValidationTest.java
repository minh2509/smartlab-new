package com.smartlab.dto.request;

import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsACompleteCreateRequest() {
        assertThat(VALIDATOR.validate(validCreateRequest())).isEmpty();
    }

    @Test
    void acceptsCreateRequestWithOnlyRequiredFields() {
        CreateProjectRequest request = new CreateProjectRequest();
        request.setCode("SL-MINIMAL");
        request.setName("Minimal project");

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void rejectsMissingRequiredCreateFields() {
        CreateProjectRequest request = new CreateProjectRequest();

        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder(
                        "code",
                        "name"
                );
    }

    @Test
    void rejectsBlankOrOversizedProjectIdentityFields() {
        CreateProjectRequest request = validCreateRequest();
        request.setCode("C".repeat(61));
        request.setName(" ");
        request.setAdditionalLeaderUserIds(List.of(" "));

        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("code", "name", "additionalLeaderUserIds[0].<list element>");
    }

    @Test
    void rejectsMoreThanOneHundredDistinctLeadersOnCreate() {
        CreateProjectRequest request = validCreateRequest();
        request.setLeaderUserId("primary-user");
        request.setAdditionalLeaderUserIds(java.util.stream.IntStream.range(0, 100)
                .mapToObj(index -> "additional-" + index)
                .toList());

        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("leaderLimitValid");
    }

    @Test
    void acceptsAnEmptyPartialUpdate() {
        assertThat(VALIDATOR.validate(new UpdateProjectRequest())).isEmpty();
    }

    @Test
    void rejectsBlankValuesWhenPartialIdentityFieldsArePresent() {
        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setCode(" ");
        request.setName("\t");

        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("code", "name");
    }

    @Test
    void rejectsBlankPrimaryLeaderOnChangeRequest() {
        ChangeProjectLeaderRequest request = new ChangeProjectLeaderRequest();
        request.setLeaderUserId(" ");

        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("leaderUserId");
    }

    @Test
    void acceptsAnEmptyCompleteLeaderSet() {
        ChangeProjectLeadersRequest request = new ChangeProjectLeadersRequest();
        request.setLeaderUserIds(List.of());

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void rejectsAMissingCompleteLeaderSet() {
        ChangeProjectLeadersRequest request = new ChangeProjectLeadersRequest();

        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("leaderUserIds");
    }

    @Test
    void rejectsBlankIdsInTheCompleteLeaderSet() {
        ChangeProjectLeadersRequest request = new ChangeProjectLeadersRequest();
        request.setLeaderUserIds(List.of("leader-user", " "));

        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("leaderUserIds[1].<list element>");
    }

    @Test
    void rejectsOversizedLegacyLeaderSetAndUserId() {
        ChangeProjectLeadersRequest request = new ChangeProjectLeadersRequest();
        request.setLeaderUserIds(java.util.stream.IntStream.range(0, 101)
                .mapToObj(index -> index == 0 ? "x".repeat(37) : "leader-" + index)
                .toList());

        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("leaderUserIds", "leaderUserIds[0].<list element>");
    }

    @Test
    void acceptsConsistentAtomicLeadershipSelection() {
        UpdateProjectLeadershipRequest request = new UpdateProjectLeadershipRequest();
        request.setPrimaryLeaderUserId("leader-user");
        request.setLeaderUserIds(List.of("leader-user", "co-leader"));

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void acceptsEmptyAtomicLeadershipWithNullPrimary() {
        UpdateProjectLeadershipRequest request = new UpdateProjectLeadershipRequest();
        request.setLeaderUserIds(List.of());

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void rejectsAtomicLeadershipWhenPrimaryIsOutsideLeaderSet() {
        UpdateProjectLeadershipRequest request = new UpdateProjectLeadershipRequest();
        request.setPrimaryLeaderUserId("outside-user");
        request.setLeaderUserIds(List.of("leader-user"));

        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("leadershipSelectionValid");
    }

    @Test
    void rejectsMissingAtomicLeaderSet() {
        UpdateProjectLeadershipRequest request = new UpdateProjectLeadershipRequest();

        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("leaderUserIds");
    }

    @Test
    void rejectsDuplicateAtomicLeaderIdsAfterTrimming() {
        UpdateProjectLeadershipRequest request = new UpdateProjectLeadershipRequest();
        request.setPrimaryLeaderUserId("leader-user");
        request.setLeaderUserIds(List.of("leader-user", " leader-user "));

        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("leaderUserIdsUnique");
    }

    @Test
    void rejectsOversizedAtomicLeadershipPayload() {
        UpdateProjectLeadershipRequest request = new UpdateProjectLeadershipRequest();
        request.setPrimaryLeaderUserId("x".repeat(37));
        request.setLeaderUserIds(java.util.stream.IntStream.range(0, 101)
                .mapToObj(index -> "leader-" + index)
                .toList());

        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("primaryLeaderUserId", "leaderUserIds");
    }

    private static CreateProjectRequest validCreateRequest() {
        CreateProjectRequest request = new CreateProjectRequest();
        request.setCode("SL-AI-2026");
        request.setName("Smart Lab AI");
        request.setProjectType(ProjectType.RESEARCH);
        request.setStatus(ProjectStatus.PROPOSED);
        request.setIsPublic(false);
        request.setIsFeatured(false);
        request.setLeaderUserId("leader-user");
        request.setAdditionalLeaderUserIds(List.of("co-leader"));
        return request;
    }
}
