package com.smartlab.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class CreateLabNewsArticleRequest {
    @NotBlank @Size(max = 500) private String title;
    @Size(max = 20_000) private String excerpt;
    @NotBlank @Size(max = 255) private String sourceName;
    @NotBlank @Size(max = 2048) private String sourceUrl;
    @NotNull private Instant publishedAt;
    private Boolean isPublic;
}
