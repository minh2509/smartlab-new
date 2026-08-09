package com.smartlab.dto.request;

import com.smartlab.enums.PostVisibility;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class CreatePostRequest {
    @NotBlank(message = "Title is required")
    @Size(max = 250)
    private String title;

    @Size(max = 500)
    private String excerpt;

    private Map<String, Object> contentJson;

    private PostVisibility visibility;

    @Positive
    private Long categoryId;

    @AssertTrue(message = "PROJECT visibility is not available when creating a post")
    public boolean isCreateVisibilityAllowed() {
        return visibility == null || visibility != PostVisibility.PROJECT;
    }
}
