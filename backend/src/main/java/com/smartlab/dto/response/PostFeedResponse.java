package com.smartlab.dto.response;

import com.smartlab.enums.PostReactionType;
import com.smartlab.enums.PostVisibility;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

@Value
@Builder
public class PostFeedResponse {
    Long id;
    String slug;
    String title;
    String excerpt;
    Map<String, Object> contentJson;
    PostAuthorResponse author;
    PostVisibility visibility;
    Long projectId;
    PostCategoryResponse category;
    Instant publishedAt;
    Instant createdAt;
    Instant updatedAt;
    PostReactionType viewerReaction;
    Map<PostReactionType, Long> reactionCounts;
    long reactionCount;
    long commentCount;
}
