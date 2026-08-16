package com.smartlab.dto.request;

import com.smartlab.enums.EventMode;
import com.smartlab.enums.EventStatus;
import com.smartlab.enums.EventVisibility;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class CreateEventRequest {
    @Positive(message = "Project id must be positive")
    private Long projectId;

    @NotBlank(message = "Event title is required")
    @Size(max = 255, message = "Event title must not exceed 255 characters")
    private String title;

    @Size(max = 20_000, message = "Event content must not exceed 20000 characters")
    private String content;

    @NotNull(message = "Event mode is required")
    private EventMode mode;

    @Pattern(regexp = ".*\\S.*", message = "Event location must not be blank")
    @Size(max = 255, message = "Event location must not exceed 255 characters")
    private String location;

    @Pattern(
            regexp = "(?i)^https?://[^\\s]+$",
            message = "Meeting URL must use http or https"
    )
    @Size(max = 2048, message = "Meeting URL must not exceed 2048 characters")
    private String meetingUrl;

    @NotNull(message = "Event start time is required")
    private Instant startAt;

    private Instant endAt;

    private EventStatus status = EventStatus.SCHEDULED;

    @NotNull(message = "Event visibility is required")
    private EventVisibility visibility;

    @AssertTrue(message = "IN_PERSON requires location only; ONLINE requires meetingUrl only")
    public boolean isModeDetailsValid() {
        if (mode == null) {
            return true;
        }
        return mode == EventMode.IN_PERSON
                ? location != null && meetingUrl == null
                : meetingUrl != null && location == null;
    }

    @AssertTrue(message = "Event end time must be after start time")
    public boolean isTimeRangeValid() {
        return startAt == null || endAt == null || endAt.isAfter(startAt);
    }

    @AssertTrue(message = "PROJECT visibility requires projectId")
    public boolean isProjectAssociationValid() {
        if (visibility == null) {
            return true;
        }
        return visibility != EventVisibility.PROJECT || projectId != null;
    }
}
