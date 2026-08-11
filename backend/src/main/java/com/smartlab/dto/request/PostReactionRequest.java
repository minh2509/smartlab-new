package com.smartlab.dto.request;

import com.smartlab.enums.PostReactionType;
import jakarta.validation.constraints.NotNull;

public record PostReactionRequest(@NotNull PostReactionType reaction) {
}
