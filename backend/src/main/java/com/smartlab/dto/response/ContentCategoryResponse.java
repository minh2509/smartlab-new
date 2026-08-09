package com.smartlab.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ContentCategoryResponse {
    Long id;
    String code;
    String name;
    String description;
}
