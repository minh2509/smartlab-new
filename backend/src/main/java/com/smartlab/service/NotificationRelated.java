package com.smartlab.service;

public record NotificationRelated(Long actorUserId, String relatedType, Long relatedId, String targetUrl) {
}
