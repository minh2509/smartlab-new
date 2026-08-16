package com.smartlab.dto.response;

import java.time.Instant;

public record PostCommentResponse(
        Long id,
        String content,
        PostAuthorResponse author,
        Instant createdAt,
        Instant updatedAt
) {
}
