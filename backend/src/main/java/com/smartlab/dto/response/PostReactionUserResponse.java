package com.smartlab.dto.response;

import com.smartlab.enums.PostReactionType;

import java.time.Instant;

public record PostReactionUserResponse(
        PostAuthorResponse user,
        PostReactionType reaction,
        Instant reactedAt
) {
}
