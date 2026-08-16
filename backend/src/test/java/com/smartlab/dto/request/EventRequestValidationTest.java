package com.smartlab.dto.request;

import com.smartlab.enums.EventMode;
import com.smartlab.enums.EventStatus;
import com.smartlab.enums.EventVisibility;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EventRequestValidationTest {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final Instant START = Instant.parse("2026-08-20T08:00:00Z");

    @Test
    void exposesExactlyTheApprovedEnums() {
        assertThat(EventMode.values()).containsExactly(EventMode.IN_PERSON, EventMode.ONLINE);
        assertThat(EventStatus.values()).containsExactly(
                EventStatus.SCHEDULED, EventStatus.CANCELLED, EventStatus.COMPLETED
        );
        assertThat(EventVisibility.values()).containsExactly(
                EventVisibility.PUBLIC, EventVisibility.LAB, EventVisibility.PROJECT
        );
    }

    @Test
    void acceptsInPersonAndOnlineModeContracts() {
        assertValid(inPerson(null, EventVisibility.LAB));

        CreateEventRequest online = base();
        online.setMode(EventMode.ONLINE);
        online.setMeetingUrl("https://meet.example/demo");
        assertValid(online);
    }

    @Test
    void rejectsMissingOrConflictingModeDetailsAndNonHttpUrl() {
        CreateEventRequest missingLocation = base();
        missingLocation.setMode(EventMode.IN_PERSON);
        assertInvalid(missingLocation);

        CreateEventRequest conflicting = inPerson(null, EventVisibility.LAB);
        conflicting.setMeetingUrl("https://meet.example/demo");
        assertInvalid(conflicting);

        CreateEventRequest badUrl = base();
        badUrl.setMode(EventMode.ONLINE);
        badUrl.setMeetingUrl("ftp://meet.example/demo");
        assertInvalid(badUrl);
    }

    @Test
    void supportsOptionalContentAndEndButRejectsNonIncreasingRange() {
        CreateEventRequest request = inPerson(null, EventVisibility.LAB);
        request.setContent(null);
        request.setEndAt(null);
        assertValid(request);

        request.setEndAt(START);
        assertInvalid(request);
        request.setEndAt(START.minusSeconds(1));
        assertInvalid(request);
    }

    @Test
    void projectVisibilityRequiresProjectButProjectMayUseEveryVisibility() {
        assertInvalid(inPerson(null, EventVisibility.PROJECT));
        assertValid(inPerson(7L, EventVisibility.PROJECT));
        assertValid(inPerson(7L, EventVisibility.LAB));
        assertValid(inPerson(7L, EventVisibility.PUBLIC));
    }

    @Test
    void enforcesTextAndIdentifierLengths() {
        CreateEventRequest request = inPerson(null, EventVisibility.LAB);
        request.setTitle("T".repeat(255));
        request.setContent("C".repeat(20_000));
        request.setLocation("L".repeat(255));
        assertValid(request);

        request.setTitle("T".repeat(256));
        assertInvalid(request);
        request.setTitle("Title");
        request.setContent("C".repeat(20_001));
        assertInvalid(request);
        request.setContent(null);
        request.setProjectId(0L);
        assertInvalid(request);
    }

    @Test
    void updateTreatsNullAsOmittedExceptExplicitProjectAssociation() {
        UpdateEventRequest request = new UpdateEventRequest();
        assertThat(VALIDATOR.validate(request)).isEmpty();
        assertThat(request.isProjectIdPresent()).isFalse();
        assertThat(request.isContentPresent()).isFalse();
        assertThat(request.isEndAtPresent()).isFalse();
        assertThat(request.isEmptyPatch()).isTrue();

        request.setContent(null);
        request.setEndAt(null);
        assertThat(request.isContentPresent()).isTrue();
        assertThat(request.isEndAtPresent()).isTrue();
        assertThat(request.isEmptyPatch()).isFalse();

        request.setProjectId(null);
        assertThat(request.isProjectIdPresent()).isTrue();
        assertThat(VALIDATOR.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("Project association is immutable");
    }

    private static CreateEventRequest base() {
        CreateEventRequest request = new CreateEventRequest();
        request.setTitle("Event title");
        request.setStartAt(START);
        request.setVisibility(EventVisibility.LAB);
        return request;
    }

    private static CreateEventRequest inPerson(Long projectId, EventVisibility visibility) {
        CreateEventRequest request = base();
        request.setProjectId(projectId);
        request.setVisibility(visibility);
        request.setMode(EventMode.IN_PERSON);
        request.setLocation("Room A");
        return request;
    }

    private static void assertValid(CreateEventRequest request) {
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    private static void assertInvalid(CreateEventRequest request) {
        Set<ConstraintViolation<CreateEventRequest>> violations = VALIDATOR.validate(request);
        assertThat(violations).isNotEmpty();
    }
}
