package com.smartlab.service.impl;

import com.smartlab.service.PostContentRenderer;
import com.smartlab.service.PostContentFileReferences;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class PlainTextPostContentRenderer implements PostContentRenderer {

    @Override
    public Optional<String> renderAndSanitize(Map<String, Object> contentJson) {
        if (contentJson == null
                || !(contentJson.get("type") instanceof String type)
                || !"doc".equals(type)) {
            return Optional.empty();
        }

        StringBuilder html = new StringBuilder();
        Set<Long> inlineImageIds = new HashSet<>();
        if (contentJson.get("content") instanceof List<?> nodes) {
            nodes.forEach(node -> renderNode(node, html, inlineImageIds));
        } else if (contentJson.get("body") instanceof String body) {
            html.append("<p>")
                    .append(escapeHtml(normalizeLineBreaks(body)).replace("\n", "<br>\n"))
                    .append("</p>");
        } else {
            return Optional.empty();
        }

        for (PostContentFileReferences.Reference reference : PostContentFileReferences.parse(contentJson)) {
            if ("image".equals(reference.type())) {
                if (inlineImageIds.contains(reference.fileId())) continue;
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

    private void renderNode(Object rawNode, StringBuilder html, Set<Long> inlineImageIds) {
        if (!(rawNode instanceof Map<?, ?> node) || !(node.get("type") instanceof String type)) {
            return;
        }

        switch (type) {
            case "text" -> renderText(node, html);
            case "paragraph" -> renderContainer(node, html, "p", alignmentClass(node), inlineImageIds);
            case "heading" -> {
                int level = boundedInteger(attribute(node, "level"), 1, 3, 2);
                renderContainer(node, html, "h" + level, alignmentClass(node), inlineImageIds);
            }
            case "bulletList" -> renderContainer(node, html, "ul", null, inlineImageIds);
            case "orderedList" -> {
                int start = boundedInteger(attribute(node, "start"), 1, Integer.MAX_VALUE, 1);
                renderContainer(node, html, "ol", start == 1 ? null : "start=\"" + start + "\"", inlineImageIds);
            }
            case "listItem" -> renderContainer(node, html, "li", null, inlineImageIds);
            case "blockquote" -> renderContainer(node, html, "blockquote", null, inlineImageIds);
            case "codeBlock" -> html.append("<pre><code>")
                    .append(escapeHtml(plainText(node)))
                    .append("</code></pre>");
            case "hardBreak" -> html.append("<br>");
            case "horizontalRule" -> html.append("<hr>");
            case "image" -> renderInlineImage(node, html, inlineImageIds);
            default -> renderChildren(node, html, inlineImageIds);
        }
    }

    private void renderInlineImage(Map<?, ?> node, StringBuilder html, Set<Long> inlineImageIds) {
        Object rawFileId = attribute(node, "fileId");
        if (!(rawFileId instanceof Number number) || number.longValue() <= 0) return;
        long fileId = number.longValue();
        Object rawAlt = attribute(node, "alt");
        String alt = rawAlt instanceof String value ? value : "";
        inlineImageIds.add(fileId);
        html.append("<figure data-post-inline-image data-file-id=\"").append(fileId).append("\">")
                .append("<img alt=\"").append(escapeHtml(alt)).append("\">")
                .append("</figure>");
    }

    private void renderText(Map<?, ?> node, StringBuilder html) {
        if (!(node.get("text") instanceof String text)) {
            return;
        }
        String rendered = escapeHtml(text);
        Object rawMarks = node.get("marks");
        if (rawMarks instanceof List<?> marks) {
            for (Object rawMark : marks) {
                if (!(rawMark instanceof Map<?, ?> mark) || !(mark.get("type") instanceof String markType)) {
                    continue;
                }
                rendered = switch (markType) {
                    case "bold" -> "<strong>" + rendered + "</strong>";
                    case "italic" -> "<em>" + rendered + "</em>";
                    case "underline" -> "<u>" + rendered + "</u>";
                    case "strike" -> "<s>" + rendered + "</s>";
                    case "code" -> "<code>" + rendered + "</code>";
                    case "link" -> renderLink(mark, rendered);
                    default -> rendered;
                };
            }
        }
        html.append(rendered);
    }

    private String renderLink(Map<?, ?> mark, String rendered) {
        Object rawAttrs = mark.get("attrs");
        if (!(rawAttrs instanceof Map<?, ?> attrs) || !(attrs.get("href") instanceof String href)) {
            return rendered;
        }
        String safeHref = safeHref(href);
        return safeHref == null
                ? rendered
                : "<a href=\"" + escapeHtml(safeHref)
                        + "\" target=\"_blank\" rel=\"nofollow noopener noreferrer\">" + rendered + "</a>";
    }

    private String safeHref(String value) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (scheme != null && (scheme.equalsIgnoreCase("http")
                    || scheme.equalsIgnoreCase("https")
                    || scheme.equalsIgnoreCase("mailto"))) {
                return uri.toString();
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid links are rendered as plain text.
        }
        return null;
    }

    private void renderContainer(Map<?, ?> node, StringBuilder html, String tag, String attribute, Set<Long> inlineImageIds) {
        html.append('<').append(tag);
        if (attribute != null && !attribute.isBlank()) {
            html.append(' ').append(attribute);
        }
        html.append('>');
        renderChildren(node, html, inlineImageIds);
        html.append("</").append(tag).append('>');
    }

    private void renderChildren(Map<?, ?> node, StringBuilder html, Set<Long> inlineImageIds) {
        if (node.get("content") instanceof List<?> children) {
            children.forEach(child -> renderNode(child, html, inlineImageIds));
        }
    }

    private String plainText(Map<?, ?> node) {
        if (node.get("text") instanceof String text) {
            return text;
        }
        if (!(node.get("content") instanceof List<?> children)) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Object child : children) {
            if (child instanceof Map<?, ?> childNode) {
                if ("hardBreak".equals(childNode.get("type"))) text.append('\n');
                else text.append(plainText(childNode));
            }
        }
        return text.toString();
    }

    private Object attribute(Map<?, ?> node, String name) {
        return node.get("attrs") instanceof Map<?, ?> attrs ? attrs.get(name) : null;
    }

    private String alignmentClass(Map<?, ?> node) {
        Object align = attribute(node, "textAlign");
        if (!(align instanceof String value)
                || !(value.equals("center") || value.equals("right") || value.equals("justify"))) {
            return null;
        }
        return "class=\"post-rich-align-" + value + "\"";
    }

    private int boundedInteger(Object value, int minimum, int maximum, int fallback) {
        if (!(value instanceof Number number)) return fallback;
        long candidate = number.longValue();
        return candidate >= minimum && candidate <= maximum ? (int) candidate : fallback;
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
