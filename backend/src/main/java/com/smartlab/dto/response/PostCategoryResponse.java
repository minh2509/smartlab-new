package com.smartlab.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PostCategoryResponse {
    Long id;
    String code;
    String name;
}
