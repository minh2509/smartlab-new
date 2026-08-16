package com.smartlab.dto.response;

import com.smartlab.enums.PostReactionType;

import java.util.Map;

public record PostReactionResponse(
        Long postId,
        PostReactionType viewerReaction,
        Map<PostReactionType, Long> reactionCounts,
        long reactionCount
) {
}
