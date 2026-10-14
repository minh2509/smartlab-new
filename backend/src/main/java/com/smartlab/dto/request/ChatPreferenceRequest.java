package com.smartlab.dto.request;

public record ChatPreferenceRequest(Boolean pinned, Boolean muted, Boolean archived) {
}
