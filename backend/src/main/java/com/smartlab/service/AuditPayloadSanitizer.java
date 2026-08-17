package com.smartlab.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AuditPayloadSanitizer {
    public static final String REDACTED = "[REDACTED]";
    public static final String BINARY_REDACTED = "[BINARY_REDACTED]";

    private static final Set<String> SENSITIVE_KEY_TOKENS = Set.of(
            "password", "token", "secret", "credential", "authorization"
    );

    private AuditPayloadSanitizer() {
    }

    public static Map<String, Object> sanitize(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        payload.forEach((key, value) -> sanitized.put(key,
                isSensitiveKey(key) ? REDACTED : sanitizeValue(value)));
        return sanitized;
    }

    private static Object sanitizeValue(Object value) {
        if (value instanceof byte[]) {
            return BINARY_REDACTED;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> {
                if (key instanceof String stringKey) {
                    sanitized.put(stringKey,
                            isSensitiveKey(stringKey) ? REDACTED : sanitizeValue(nestedValue));
                } else {
                    throw new IllegalArgumentException("Audit JSON keys must be strings");
                }
            });
            return sanitized;
        }
        if (value instanceof List<?> list) {
            List<Object> sanitized = new ArrayList<>(list.size());
            list.forEach(item -> sanitized.add(sanitizeValue(item)));
            return sanitized;
        }
        return value;
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (normalized.equals("meetingurl")) {
            return true;
        }
        String tokenized = key.replaceAll("(?<=[a-z])(?=[A-Z])", " ")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ");
        for (String token : tokenized.split(" ")) {
            if (SENSITIVE_KEY_TOKENS.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
