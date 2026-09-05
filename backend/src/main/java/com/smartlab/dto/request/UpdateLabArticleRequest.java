package com.smartlab.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.smartlab.enums.LabArticleStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public class UpdateLabArticleRequest {
    @JsonIgnore private boolean titlePresent;
    @JsonIgnore private boolean slugPresent;
    @JsonIgnore private boolean excerptPresent;
    @JsonIgnore private boolean contentPresent;
    @JsonIgnore private boolean statusPresent;
    @JsonIgnore private boolean publishedAtPresent;
    @Size(max = 500) private String title;
    @Size(max = 500) private String slug;
    @Size(max = 20_000) private String excerpt;
    private Map<String, Object> content;
    private LabArticleStatus status;
    private Instant publishedAt;

    public String getTitle() { return title; }
    @JsonSetter("title") public void setTitle(String value) { titlePresent = true; title = value; }
    public boolean hasTitle() { return titlePresent; }
    public String getSlug() { return slug; }
    @JsonSetter("slug") public void setSlug(String value) { slugPresent = true; slug = value; }
    public boolean hasSlug() { return slugPresent; }
    public String getExcerpt() { return excerpt; }
    @JsonSetter("excerpt") public void setExcerpt(String value) { excerptPresent = true; excerpt = value; }
    public boolean hasExcerpt() { return excerptPresent; }
    public Map<String, Object> getContent() { return content; }
    @JsonSetter("content") public void setContent(Map<String, Object> value) { contentPresent = true; content = value; }
    public boolean hasContent() { return contentPresent; }
    public LabArticleStatus getStatus() { return status; }
    @JsonSetter("status") public void setStatus(LabArticleStatus value) { statusPresent = true; status = value; }
    public boolean hasStatus() { return statusPresent; }
    public Instant getPublishedAt() { return publishedAt; }
    @JsonSetter("publishedAt") public void setPublishedAt(Instant value) { publishedAtPresent = true; publishedAt = value; }
    public boolean hasPublishedAt() { return publishedAtPresent; }

    @AssertTrue(message = "At least one mutable article field must be provided") @JsonIgnore
    public boolean isPatchNotEmpty() { return titlePresent || slugPresent || excerptPresent || contentPresent || statusPresent || publishedAtPresent; }
    @AssertTrue(message = "title must be nonblank and at most 500 characters when provided") @JsonIgnore
    public boolean isTitleValid() { return !titlePresent || (title != null && !title.isBlank() && title.length() <= 500); }
    @AssertTrue(message = "content is required when provided") @JsonIgnore
    public boolean isContentValid() { return !contentPresent || content != null; }
    @AssertTrue(message = "status is required when provided") @JsonIgnore
    public boolean isStatusValid() { return !statusPresent || status != null; }
    @AssertTrue(message = "publishedAt is required when provided") @JsonIgnore
    public boolean isPublishedAtValid() { return !publishedAtPresent || publishedAt != null; }
}
