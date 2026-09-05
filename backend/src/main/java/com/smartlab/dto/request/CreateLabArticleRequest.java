package com.smartlab.dto.request;

import com.smartlab.enums.LabArticleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class CreateLabArticleRequest {
    @NotBlank @Size(max = 500) private String title;
    @Size(max = 500) private String slug;
    @Size(max = 20_000) private String excerpt;
    @NotNull private Map<String, Object> content;
    private LabArticleStatus status;
    private Instant publishedAt;
}
