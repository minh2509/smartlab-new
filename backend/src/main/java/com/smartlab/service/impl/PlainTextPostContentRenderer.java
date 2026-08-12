package com.smartlab.service.impl;

import com.smartlab.service.PostContentRenderer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class PlainTextPostContentRenderer implements PostContentRenderer {

    @Override
    public Optional<String> renderAndSanitize(Map<String, Object> contentJson) {
        if (contentJson == null
                || !(contentJson.get("type") instanceof String type)
                || !"doc".equals(type)
                || !(contentJson.get("body") instanceof String body)) {
            return Optional.empty();
        }

        return Optional.of("<p>" + escapeHtml(normalizeLineBreaks(body)).replace("\n", "<br>\n") + "</p>");
    }

    private String normalizeLineBreaks(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
