package com.smartlab.dto.response;

import com.smartlab.enums.PublicationType;
import lombok.Builder;
import java.time.Instant;
import java.time.LocalDate;

@Builder
public record ResearchPublicationResponse(Long id, String title, String authors, PublicationType publicationType,
        String venue, Integer publicationYear, LocalDate publicationDate, String doi, String publicUrl,
        String summary, Boolean isPublic, Instant createdAt, Instant updatedAt) { }
