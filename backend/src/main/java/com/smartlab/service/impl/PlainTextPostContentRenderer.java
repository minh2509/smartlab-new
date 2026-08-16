package com.smartlab.service.impl;

import com.smartlab.service.PostContentRenderer;
import com.smartlab.service.PostContentFileReferences;
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

        StringBuilder html = new StringBuilder("<p>")
                .append(escapeHtml(normalizeLineBreaks(body)).replace("\n", "<br>\n"))
                .append("</p>");
        for (PostContentFileReferences.Reference reference : PostContentFileReferences.parse(contentJson)) {
            if ("image".equals(reference.type())) {
                html.append("<figure data-file-id=\"").append(reference.fileId()).append("\">")
                        .append("<img alt=\"").append(escapeHtml(reference.alt() == null ? "" : reference.alt()))
                        .append("\">")
                        .append("</figure>");
            } else {
                html.append("<span data-file-id=\"").append(reference.fileId()).append("\">")
                        .append(escapeHtml(reference.label() == null ? "" : reference.label()))
                        .append("</span>");
            }
        }
        return Optional.of(html.toString());
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
