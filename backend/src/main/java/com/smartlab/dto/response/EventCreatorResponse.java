package com.smartlab.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EventCreatorResponse {
    String userId;
    String name;
}
