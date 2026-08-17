package com.smartlab.dto.response;

import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

@Value
@Builder
public class PostDetailResponse {
    Long id;
    String title;
    String slug;
    String excerpt;
    Map<String, Object> contentJson;
    PostVisibility visibility;
    Long projectId;
    PostStatus status;
    PostCategoryResponse category;
    PostAuthorResponse author;
    PostReviewFeedbackResponse reviewFeedback;
    Instant publishedAt;
    Instant createdAt;
    Instant updatedAt;
}
