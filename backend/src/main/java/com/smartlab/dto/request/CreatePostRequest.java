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

    @Positive
    private Long projectId;

    @AssertTrue(message = "PROJECT visibility requires projectId")
    public boolean isProjectAssociationValid() {
        return visibility != PostVisibility.PROJECT || projectId != null;
    }

    @AssertTrue(message = "PUBLIC and LAB visibility must not include projectId")
    public boolean isNonProjectVisibilityAssociationValid() {
        return visibility == PostVisibility.PROJECT || projectId == null;
    }
}
