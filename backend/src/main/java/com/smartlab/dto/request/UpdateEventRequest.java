package com.smartlab.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.smartlab.enums.EventMode;
import com.smartlab.enums.EventStatus;
import com.smartlab.enums.EventVisibility;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class UpdateEventRequest {
    @Positive(message = "Project id must be positive")
    private Long projectId;

    @JsonIgnore
    private boolean projectIdPresent;

    @Pattern(regexp = ".*\\S.*", message = "Event title must not be blank")
    @Size(max = 255, message = "Event title must not exceed 255 characters")
    private String title;

    @Size(max = 20_000, message = "Event content must not exceed 20000 characters")
    private String content;

    @JsonIgnore
    private boolean contentPresent;

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

    private Instant startAt;
    private Instant endAt;

    @JsonIgnore
    private boolean endAtPresent;

    private EventStatus status;
    private EventVisibility visibility;

    @JsonSetter("projectId")
    public void setProjectId(Long projectId) {
        this.projectId = projectId;
        this.projectIdPresent = true;
    }

    @JsonSetter("content")
    public void setContent(String content) {
        this.content = content;
        this.contentPresent = true;
    }

    @JsonSetter("endAt")
    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
        this.endAtPresent = true;
    }

    @AssertFalse(message = "Project association is immutable")
    public boolean isProjectAssociationChangeRequested() {
        return projectIdPresent;
    }

    @JsonIgnore
    public boolean isEmptyPatch() {
        return !projectIdPresent
                && title == null
                && !contentPresent
                && mode == null
                && location == null
                && meetingUrl == null
                && startAt == null
                && !endAtPresent
                && status == null
                && visibility == null;
    }
}
