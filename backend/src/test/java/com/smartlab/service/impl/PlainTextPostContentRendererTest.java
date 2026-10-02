package com.smartlab.service.impl;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;

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

    @Test
    void rendersFileMetadataWithoutTrustingAnyClientUrlAndEscapesAltAndLabel() {
        Map<String, Object> content = Map.of(
                "type", "doc",
                "body", "body",
                "files", List.of(
                        Map.of("type", "image", "fileId", 12, "alt", "<unsafe>&\"'"),
                        Map.of("type", "file", "fileId", 13, "label", "<download>&\"'")
                )
        );

        assertThat(renderer.renderAndSanitize(content)).hasValueSatisfying(html ->
                assertThat(html)
                        .contains("<figure data-file-id=\"12\"><img alt=\"&lt;unsafe&gt;&amp;&quot;&#39;\"></figure>")
                        .contains("<span data-file-id=\"13\">&lt;download&gt;&amp;&quot;&#39;</span>")
                        .doesNotContain("src=", "href=", "storageKey", "publicUrl")
        );
    }

    @Test
    void rendersStructuredRichTextFromWhitelistedNodesAndMarks() {
        Map<String, Object> content = Map.of(
                "type", "doc",
                "content", List.of(
                        Map.of("type", "heading", "attrs", Map.of("level", 2), "content", List.of(
                                Map.of("type", "text", "text", "Smart Lab", "marks", List.of(Map.of("type", "bold")))
                        )),
                        Map.of("type", "paragraph", "content", List.of(
                                Map.of("type", "text", "text", "Read "),
                                Map.of("type", "text", "text", "the docs", "marks", List.of(Map.of(
                                        "type", "link",
                                        "attrs", Map.of("href", "https://example.com/docs")
                                )))
                        )),
                        Map.of("type", "bulletList", "content", List.of(
                                Map.of("type", "listItem", "content", List.of(
                                        Map.of("type", "paragraph", "content", List.of(
                                                Map.of("type", "text", "text", "First")
                                        ))
                                ))
                        ))
                )
        );

        assertThat(renderer.renderAndSanitize(content)).hasValue(
                "<h2><strong>Smart Lab</strong></h2>"
                        + "<p>Read <a href=\"https://example.com/docs\" target=\"_blank\" "
                        + "rel=\"nofollow noopener noreferrer\">the docs</a></p>"
                        + "<ul><li><p>First</p></li></ul>"
        );
    }

    @Test
    void structuredRendererEscapesTextAndDropsUnsafeLinksAndUnknownMarkup() {
        Map<String, Object> content = Map.of(
                "type", "doc",
                "content", List.of(
                        Map.of("type", "paragraph", "attrs", Map.of("onclick", "alert(1)"), "content", List.of(
                                Map.of("type", "text", "text", "<script>alert(1)</script>", "marks", List.of(Map.of(
                                        "type", "link",
                                        "attrs", Map.of("href", "javascript:alert(1)", "onclick", "alert(1)")
                                )))
                        )),
                        Map.of("type", "rawHtml", "content", List.of(
                                Map.of("type", "text", "text", "safe fallback")
                        ))
                )
        );

        assertThat(renderer.renderAndSanitize(content)).hasValueSatisfying(html -> assertThat(html)
                .isEqualTo("<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>safe fallback")
                .doesNotContain("javascript:", "onclick=", "<script>"));
    }

    @Test
    void rendersInlineImageAtItsDocumentPositionWithoutAppendingItAgain() {
        Map<String, Object> content = Map.of(
                "type", "doc",
                "content", List.of(
                        Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", "Before"))),
                        Map.of("type", "image", "attrs", Map.of("fileId", 12, "alt", "Lab <photo>")),
                        Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", "After")))
                ),
                "files", List.of(Map.of("type", "image", "fileId", 12, "alt", "Lab <photo>"))
        );

        assertThat(renderer.renderAndSanitize(content)).hasValue(
                "<p>Before</p>"
                        + "<figure data-post-inline-image data-file-id=\"12\"><img alt=\"Lab &lt;photo&gt;\"></figure>"
                        + "<p>After</p>"
        );
    }
}
