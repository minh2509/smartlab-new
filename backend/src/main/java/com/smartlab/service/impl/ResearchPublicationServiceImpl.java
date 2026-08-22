package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateResearchPublicationRequest;
import com.smartlab.dto.request.UpdateResearchPublicationRequest;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.dto.response.PublicationYearCountResponse;
import com.smartlab.dto.response.ResearchPublicationResponse;
import com.smartlab.entity.ResearchPublicationEntity;
import com.smartlab.enums.PublicationType;
import com.smartlab.repo.ResearchPublicationRepository;
import com.smartlab.service.ResearchPublicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ResearchPublicationServiceImpl implements ResearchPublicationService {
    private static final int PUBLIC_PAGE_SIZE_MAX = 24;
    private final ResearchPublicationRepository publicationRepository;

    @Override @Transactional(readOnly = true)
    public List<PublicationYearCountResponse> listPublicYears() {
        return publicationRepository.findPublicYearCounts().stream()
                .map(row -> new PublicationYearCountResponse(((Number) row[0]).intValue(), ((Number) row[1]).longValue()))
                .toList();
    }

    @Override @Transactional(readOnly = true)
    public PublicPageResponse<ResearchPublicationResponse> listPublic(Integer year, int page, int size) {
        if (page < 0) throw badRequest("Page must not be negative");
        if (size < 1 || size > PUBLIC_PAGE_SIZE_MAX) throw badRequest("Size must be between 1 and 24");
        Integer selectedYear = year == null ? publicationRepository.findNewestPublicYear() : year;
        if (selectedYear == null) return new PublicPageResponse<>(List.of(), page, size, 0, 0);
        Page<ResearchPublicationResponse> result = publicationRepository
                .findPublicByYear(selectedYear, PageRequest.of(page, size)).map(this::toResponse);
        return PublicPageResponse.from(result);
    }

    @Override @Transactional
    public ResearchPublicationResponse create(CreateResearchPublicationRequest request) {
        ResearchPublicationEntity saved = publicationRepository.saveAndFlush(ResearchPublicationEntity.create(
                required(request.getTitle(), "Title is required"), required(request.getAuthors(), "Authors are required"),
                requireType(request.getPublicationType()), required(request.getVenue(), "Venue is required"),
                requireYear(request.getPublicationYear()), request.getPublicationDate(), optional(request.getDoi()),
                optionalUrl(request.getPublicUrl()), optional(request.getSummary()), Boolean.TRUE.equals(request.getIsPublic()), Instant.now()
        ));
        return toResponse(saved);
    }

    @Override @Transactional
    public ResearchPublicationResponse update(Long id, UpdateResearchPublicationRequest request) {
        ResearchPublicationEntity publication = findActive(id);
        publication.update(
                request.getTitle() == null ? publication.getTitle() : required(request.getTitle(), "Title is required"),
                request.getAuthors() == null ? publication.getAuthors() : required(request.getAuthors(), "Authors are required"),
                request.getPublicationType() == null ? publication.getPublicationType() : requireType(request.getPublicationType()),
                request.getVenue() == null ? publication.getVenue() : required(request.getVenue(), "Venue is required"),
                request.getPublicationYear() == null ? publication.getPublicationYear() : requireYear(request.getPublicationYear()),
                request.isPublicationDatePresent() ? request.getPublicationDate() : publication.getPublicationDate(),
                request.isDoiPresent() ? optional(request.getDoi()) : publication.getDoi(),
                request.isPublicUrlPresent() ? optionalUrl(request.getPublicUrl()) : publication.getPublicUrl(),
                request.isSummaryPresent() ? optional(request.getSummary()) : publication.getSummary(),
                request.getIsPublic() == null ? publication.getIsPublic() : request.getIsPublic(), Instant.now()
        );
        return toResponse(publicationRepository.saveAndFlush(publication));
    }

    @Override @Transactional
    public void delete(Long id) { ResearchPublicationEntity publication = findActive(id); publication.softDelete(Instant.now()); publicationRepository.saveAndFlush(publication); }

    private ResearchPublicationEntity findActive(Long id) { return publicationRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publication not found")); }
    private PublicationType requireType(PublicationType type) { if (type == null) throw badRequest("Publication type is required"); return type; }
    private Integer requireYear(Integer year) { if (year == null || year < 1900 || year > 2100) throw badRequest("Publication year must be between 1900 and 2100"); return year; }
    private String required(String value, String message) { String normalized = optional(value); if (normalized == null) throw badRequest(message); return normalized; }
    private String optional(String value) { if (value == null) return null; String normalized = value.trim(); return normalized.isEmpty() ? null : normalized; }
    private String optionalUrl(String value) { String normalized = optional(value); if (normalized != null && !(normalized.startsWith("http://") || normalized.startsWith("https://"))) throw badRequest("Public URL must be an HTTP(S) URL"); return normalized; }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResearchPublicationResponse toResponse(ResearchPublicationEntity p) { return ResearchPublicationResponse.builder().id(p.getId()).title(p.getTitle()).authors(p.getAuthors()).publicationType(p.getPublicationType()).venue(p.getVenue()).publicationYear(p.getPublicationYear()).publicationDate(p.getPublicationDate()).doi(p.getDoi()).publicUrl(p.getPublicUrl()).summary(p.getSummary()).isPublic(p.getIsPublic()).createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt()).build(); }
}
