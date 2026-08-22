package com.smartlab.dto.request;

import com.smartlab.enums.PublicationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateResearchPublicationRequest {
    @NotBlank @Size(max = 500) private String title;
    @NotBlank @Size(max = 20_000) private String authors;
    @NotNull private PublicationType publicationType;
    @NotBlank @Size(max = 500) private String venue;
    @NotNull @Min(1900) @Max(2100) private Integer publicationYear;
    private LocalDate publicationDate;
    @Size(max = 255) @Pattern(regexp = "^(https?://doi\\.org/)?[^\\s]+$", message = "DOI must be a DOI or DOI URL") private String doi;
    @Size(max = 2048) @Pattern(regexp = "^https?://.+$", message = "Public URL must be an HTTP(S) URL") private String publicUrl;
    @Size(max = 20_000) private String summary;
    private Boolean isPublic;
}
