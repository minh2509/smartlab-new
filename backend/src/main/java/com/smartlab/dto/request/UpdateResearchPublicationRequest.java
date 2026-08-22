package com.smartlab.dto.request;

import com.smartlab.enums.PublicationType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;

@Data
public class UpdateResearchPublicationRequest {
    @Size(min = 1, max = 500) private String title;
    @Size(min = 1, max = 20_000) private String authors;
    private PublicationType publicationType;
    @Size(min = 1, max = 500) private String venue;
    @Min(1900) @Max(2100) private Integer publicationYear;
    private LocalDate publicationDate;
    @JsonIgnore
    private boolean publicationDatePresent;
    @Size(max = 255) @Pattern(regexp = "^(https?://doi\\.org/)?[^\\s]+$", message = "DOI must be a DOI or DOI URL") private String doi;
    @JsonIgnore
    private boolean doiPresent;
    @Size(max = 2048) @Pattern(regexp = "^https?://.+$", message = "Public URL must be an HTTP(S) URL") private String publicUrl;
    @JsonIgnore
    private boolean publicUrlPresent;
    @Size(max = 20_000) private String summary;
    @JsonIgnore
    private boolean summaryPresent;
    private Boolean isPublic;

    @JsonSetter("publicationDate") public void setPublicationDate(LocalDate value) { publicationDate = value; publicationDatePresent = true; }
    @JsonSetter("doi") public void setDoi(String value) { doi = value; doiPresent = true; }
    @JsonSetter("publicUrl") public void setPublicUrl(String value) { publicUrl = value; publicUrlPresent = true; }
    @JsonSetter("summary") public void setSummary(String value) { summary = value; summaryPresent = true; }
}
