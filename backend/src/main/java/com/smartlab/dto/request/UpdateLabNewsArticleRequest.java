package com.smartlab.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class UpdateLabNewsArticleRequest {
    @Size(max = 500) private String title;
    @Size(max = 255) private String sourceName;
    @Size(max = 2048) private String sourceUrl;
    private Instant publishedAt;
    private Boolean isPublic;
    @Size(max = 20_000) private String excerpt;
    @JsonIgnore private boolean titlePresent;
    @JsonIgnore private boolean sourceNamePresent;
    @JsonIgnore private boolean sourceUrlPresent;
    @JsonIgnore private boolean publishedAtPresent;
    @JsonIgnore private boolean excerptPresent;

    @JsonSetter("title")
    public void setTitle(String value) { title = value; titlePresent = true; }

    @JsonSetter("sourceName")
    public void setSourceName(String value) { sourceName = value; sourceNamePresent = true; }

    @JsonSetter("sourceUrl")
    public void setSourceUrl(String value) { sourceUrl = value; sourceUrlPresent = true; }

    @JsonSetter("publishedAt")
    public void setPublishedAt(Instant value) { publishedAt = value; publishedAtPresent = true; }

    @JsonSetter("excerpt")
    public void setExcerpt(String value) {
        excerpt = value;
        excerptPresent = true;
    }
}
