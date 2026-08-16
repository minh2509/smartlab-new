package com.smartlab.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses the additive post-content file reference schema without trusting client media locations. */
public final class PostContentFileReferences {
    private static final Set<String> FORBIDDEN_MEDIA_SOURCE_FIELDS = Set.of("storageKey", "publicUrl", "src", "href");

    private PostContentFileReferences() {
    }

    public static List<Reference> parse(Map<String, Object> contentJson) {
        if (contentJson == null || !contentJson.containsKey("files")) {
            return List.of();
        }
        Object rawFiles = contentJson.get("files");
        if (!(rawFiles instanceof List<?> files)) {
            throw invalid();
        }

        List<Reference> references = new ArrayList<>();
        Set<Long> fileIds = new HashSet<>();
        for (Object rawFile : files) {
            if (!(rawFile instanceof Map<?, ?> rawReference)) {
                throw invalid();
            }
            if (FORBIDDEN_MEDIA_SOURCE_FIELDS.stream().anyMatch(rawReference::containsKey)) {
                throw invalid();
            }
            Object rawType = rawReference.get("type");
            if (!(rawType instanceof String type) || !("image".equals(type) || "file".equals(type))) {
                throw invalid();
            }
            Long fileId = positiveIntegralId(rawReference.get("fileId"));
            if (fileId == null || !fileIds.add(fileId)) {
                throw invalid();
            }
            String alt = textOrNull(rawReference.get("alt"));
            String label = textOrNull(rawReference.get("label"));
            if ((rawReference.containsKey("alt") && alt == null)
                    || (rawReference.containsKey("label") && label == null)) {
                throw invalid();
            }
            references.add(new Reference(type, fileId, alt, label));
        }
        return List.copyOf(references);
    }

    private static Long positiveIntegralId(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        try {
            BigDecimal decimal = new BigDecimal(number.toString());
            long id = decimal.longValueExact();
            return id > 0 ? id : null;
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    private static String textOrNull(Object value) {
        return value == null ? null : value instanceof String text ? text : null;
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid post content file reference");
    }

    public record Reference(String type, Long fileId, String alt, String label) {
    }
}
