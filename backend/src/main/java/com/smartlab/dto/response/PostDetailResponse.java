package com.smartlab.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class PostDetailResponse {
    Long id;
    String title;
    String slug;
    String excerpt;
    JsonNode contentJson;
    PostVisibility visibility;
    PostStatus status;
    PostCategoryResponse category;
    Instant publishedAt;
    Instant createdAt;
    Instant updatedAt;
}
