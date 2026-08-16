package com.smartlab.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostContentFileReferencesTest {

    @Test
    void legacyDocumentWithoutFilesRemainsCompatible() {
        assertThat(PostContentFileReferences.parse(Map.of("type", "doc", "body", "plain text"))).isEmpty();
    }

    @Test
    void acceptsPositiveNumericReferencesAndRejectsDuplicateIdsDeterministically() {
        assertThat(PostContentFileReferences.parse(Map.of("files", List.of(
                Map.of("type", "image", "fileId", 12, "alt", "alt"),
                Map.of("type", "file", "fileId", 13L, "label", "label")
        )))).extracting(PostContentFileReferences.Reference::fileId).containsExactly(12L, 13L);

        assertInvalid(Map.of("files", List.of(
                Map.of("type", "image", "fileId", 12),
                Map.of("type", "file", "fileId", 12)
        )));
    }

    @Test
    void rejectsInvalidTypesIdsAndUntrustedMediaLocations() {
        assertInvalid(Map.of("files", List.of(Map.of("type", "video", "fileId", 1))));
        assertInvalid(Map.of("files", List.of(Map.of("type", "file", "fileId", 0))));
        assertInvalid(Map.of("files", List.of(Map.of("type", "image", "fileId", 1, "src", "https://example.test/x"))));
    }

    private static void assertInvalid(Map<String, Object> content) {
        assertThatThrownBy(() -> PostContentFileReferences.parse(content))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
