package com.smartlab.service.impl;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlainTextPostContentRendererTest {

    private final PlainTextPostContentRenderer renderer = new PlainTextPostContentRenderer();

    @Test
    void rendersCanonicalDocumentBodyAsServerGeneratedHtml() {
        assertThat(renderer.renderAndSanitize(Map.of(
                "type", "doc",
                "body", "Hello, Smart Lab"
        ))).contains("<p>Hello, Smart Lab</p>");
    }

    @Test
    void escapesHostileHtmlInsteadOfTrustingItAsMarkup() {
        String body = "<script>alert(1)</script><img src=x onerror=alert(1)><b>hello</b>";

        assertThat(renderer.renderAndSanitize(Map.of("type", "doc", "body", body)))
                .contains("<p>&lt;script&gt;alert(1)&lt;/script&gt;&lt;img src=x onerror=alert(1)&gt;&lt;b&gt;hello&lt;/b&gt;</p>");
    }

    @Test
    void escapesAmpersandsAndAngleBrackets() {
        assertThat(renderer.renderAndSanitize(Map.of(
                "type", "doc",
                "body", "A & B < C > D"
        ))).contains("<p>A &amp; B &lt; C &gt; D</p>");
    }

    @Test
    void preservesLineBreaksWithSafeBreakElements() {
        assertThat(renderer.renderAndSanitize(Map.of(
                "type", "doc",
                "body", "first\r\nsecond\rthird\nfourth"
        ))).contains("<p>first<br>\nsecond<br>\nthird<br>\nfourth</p>");
    }

    @Test
    void rendersBlankAndEmptyBodiesDeterministically() {
        assertThat(renderer.renderAndSanitize(Map.of("type", "doc", "body", "")))
                .contains("<p></p>");
        assertThat(renderer.renderAndSanitize(Map.of("type", "doc", "body", "   ")))
                .contains("<p>   </p>");
    }

    @Test
    void returnsEmptyForUnsupportedSchemas() {
        assertThat(renderer.renderAndSanitize(Map.of("body", "missing type")))
                .isEqualTo(Optional.empty());
        assertThat(renderer.renderAndSanitize(Map.of("type", "legacy", "body", "text")))
                .isEqualTo(Optional.empty());
        assertThat(renderer.renderAndSanitize(Map.of("type", "doc")))
                .isEqualTo(Optional.empty());
        assertThat(renderer.renderAndSanitize(Map.of("type", "doc", "body", 42)))
                .isEqualTo(Optional.empty());
    }

    @Test
    void doesNotMutateInputMap() {
        Map<String, Object> contentJson = new LinkedHashMap<>();
        contentJson.put("type", "doc");
        contentJson.put("body", "<b>unchanged input</b>");
        Map<String, Object> before = new LinkedHashMap<>(contentJson);

        renderer.renderAndSanitize(contentJson);

        assertThat(contentJson).isEqualTo(before);
    }
}
