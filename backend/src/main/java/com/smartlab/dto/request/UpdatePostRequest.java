package com.smartlab.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.smartlab.enums.PostVisibility;
import jakarta.validation.constraints.AssertTrue;

import java.util.Map;

public class UpdatePostRequest {

    @JsonIgnore
    private boolean titlePresent;

    private String title;

    @JsonIgnore
    private boolean excerptPresent;

    private String excerpt;

    @JsonIgnore
    private boolean contentJsonPresent;

    private Map<String, Object> contentJson;

    @JsonIgnore
    private boolean visibilityPresent;

    private PostVisibility visibility;

    @JsonIgnore
    private boolean categoryIdPresent;

    private Long categoryId;

    @JsonIgnore
    private boolean projectIdPresent;

    private Long projectId;

    public String getTitle() {
        return title;
    }

    @JsonSetter("title")
    public void setTitle(String title) {
        this.titlePresent = true;
        this.title = title;
    }

    public boolean hasTitle() {
        return titlePresent;
    }

    public String getExcerpt() {
        return excerpt;
    }

    @JsonSetter("excerpt")
    public void setExcerpt(String excerpt) {
        this.excerptPresent = true;
        this.excerpt = excerpt;
    }

    public boolean hasExcerpt() {
        return excerptPresent;
    }

    public Map<String, Object> getContentJson() {
        return contentJson;
    }

    @JsonSetter("contentJson")
    public void setContentJson(Map<String, Object> contentJson) {
        this.contentJsonPresent = true;
        this.contentJson = contentJson;
    }

    public boolean hasContentJson() {
        return contentJsonPresent;
    }

    public PostVisibility getVisibility() {
        return visibility;
    }

    @JsonSetter("visibility")
    public void setVisibility(PostVisibility visibility) {
        this.visibilityPresent = true;
        this.visibility = visibility;
    }

    public boolean hasVisibility() {
        return visibilityPresent;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    @JsonSetter("categoryId")
    public void setCategoryId(Long categoryId) {
        this.categoryIdPresent = true;
        this.categoryId = categoryId;
    }

    public boolean hasCategoryId() {
        return categoryIdPresent;
    }

    public Long getProjectId() {
        return projectId;
    }

    @JsonSetter("projectId")
    public void setProjectId(Long projectId) {
        this.projectIdPresent = true;
        this.projectId = projectId;
    }

    public boolean hasProjectId() {
        return projectIdPresent;
    }

    @AssertTrue(message = "At least one mutable post field must be provided")
    @JsonIgnore
    public boolean isPatchNotEmpty() {
        return titlePresent || excerptPresent || contentJsonPresent || visibilityPresent || categoryIdPresent
                || projectIdPresent;
    }

    @AssertTrue(message = "title must be nonblank and at most 250 characters when provided")
    @JsonIgnore
    public boolean isTitleValid() {
        return !titlePresent || (title != null && !title.isBlank() && title.length() <= 250);
    }

    @AssertTrue(message = "excerpt must be at most 500 characters when provided")
    @JsonIgnore
    public boolean isExcerptValid() {
        return !excerptPresent || excerpt == null || excerpt.length() <= 500;
    }

    @AssertTrue(message = "visibility is required when provided")
    @JsonIgnore
    public boolean isVisibilityValid() {
        return !visibilityPresent || visibility != null;
    }

    @AssertTrue(message = "categoryId must be positive when provided")
    @JsonIgnore
    public boolean isCategoryIdValid() {
        return !categoryIdPresent || categoryId == null || categoryId > 0;
    }

    @AssertTrue(message = "projectId must be positive when provided")
    @JsonIgnore
    public boolean isProjectIdValid() {
        return !projectIdPresent || projectId == null || projectId > 0;
    }
}
