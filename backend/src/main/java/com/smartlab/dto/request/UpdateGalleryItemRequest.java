package com.smartlab.dto.request;

import com.smartlab.enums.GalleryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class UpdateGalleryItemRequest {
    @NotBlank(message = "Gallery title is required")
    @Size(max = 255)
    private String title;

    @Size(max = 20_000)
    private String caption;

    @NotBlank(message = "Gallery alt text is required")
    @Size(max = 255)
    private String altText;

    private GalleryCategory category;
    private Long projectId;
    private Long eventId;
    private Instant capturedAt;
    private Boolean isFeatured;
}
