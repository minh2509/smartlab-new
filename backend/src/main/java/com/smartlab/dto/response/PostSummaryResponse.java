package com.smartlab.dto.response;

import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class PostSummaryResponse {
    Long id;
    String title;
    String slug;
    String excerpt;
    PostVisibility visibility;
    Long projectId;
    PostStatus status;
    PostCategoryResponse category;
    PostAuthorResponse author;
    Instant publishedAt;
    Instant createdAt;
    Instant updatedAt;
}
