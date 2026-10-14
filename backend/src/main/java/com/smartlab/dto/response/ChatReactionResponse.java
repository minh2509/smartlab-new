package com.smartlab.dto.response;

import java.util.List;

public record ChatReactionResponse(String emoji, long count, List<String> userIds) {
}
