package com.smartlab.dto.response;

import java.util.List;

public record CursorPageResponse<T>(List<T> items, String nextCursor) {
}
