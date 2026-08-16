package com.smartlab.dto.response;

import com.smartlab.enums.EventMode;
import com.smartlab.enums.EventStatus;
import com.smartlab.enums.EventVisibility;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class EventResponse {
    Long id;
    Long projectId;
    String title;
    String content;
    EventMode mode;
    String location;
    String meetingUrl;
    Instant startAt;
    Instant endAt;
    EventStatus status;
    EventVisibility visibility;
    EventCreatorResponse creator;
    Instant createdAt;
    Instant updatedAt;
    Instant deletedAt;
}
