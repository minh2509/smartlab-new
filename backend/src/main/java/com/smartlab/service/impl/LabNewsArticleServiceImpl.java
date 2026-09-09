package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateLabNewsArticleRequest;
import com.smartlab.dto.request.UpdateLabNewsArticleRequest;
import com.smartlab.dto.response.LabNewsArticleResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.entity.LabNewsArticleEntity;
import com.smartlab.enums.PublicNewsSort;
import com.smartlab.repo.LabNewsArticleRepository;
import com.smartlab.service.LabNewsArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LabNewsArticleServiceImpl implements LabNewsArticleService {
    private static final int PUBLIC_LIMIT_MAX = 12;
    private final LabNewsArticleRepository articleRepository;

    @Override
    @Transactional(readOnly = true)
    public List<LabNewsArticleResponse> listPublic(int limit) {
        if (limit < 1 || limit > PUBLIC_LIMIT_MAX) throw badRequest("Limit must be between 1 and 12");
        return articleRepository.findNewestPublic(PageRequest.of(0, limit)).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PublicPageResponse<LabNewsArticleResponse> listPublicArchive(int page, int size) {
        if (page < 0) throw badRequest("Page must not be negative");
        if (size < 1 || size > 48) throw badRequest("Size must be between 1 and 48");
        return PublicPageResponse.from(articleRepository.findPublicArchive(PageRequest.of(page, size)).map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicPageResponse<LabNewsArticleResponse> listPublicArchive(String query, String source, Integer year,
                                                                         PublicNewsSort sort, int page, int size) {
        if (page < 0) throw badRequest("Page must not be negative");
        if (size < 1 || size > 48) throw badRequest("Size must be between 1 and 48");
        if (year != null && (year < 2000 || year > Year.now().getValue())) throw badRequest("Year is invalid");
        String normalizedQuery = optional(query);
        String normalizedSource = optional(source);
        PublicNewsSort normalizedSort = sort == null ? PublicNewsSort.LATEST : sort;
        Sort.Direction direction = normalizedSort == PublicNewsSort.OLDEST ? Sort.Direction.ASC : Sort.Direction.DESC;
        Specification<LabNewsArticleEntity> specification = publicArchiveSpecification(normalizedQuery, normalizedSource, year);
        return PublicPageResponse.from(articleRepository.findAll(specification,
                PageRequest.of(page, size, Sort.by(direction, "publishedAt", "id"))).map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listPublicSources() {
        Set<String> seen = new HashSet<>();
        return articleRepository.findPublicSources().stream()
                .map(this::optional)
                .filter(source -> source != null)
                .filter(source -> seen.add(source.toLowerCase(Locale.ROOT)))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> listPublicYears() {
        return articleRepository.findPublicYears();
    }

    @Override
    @Transactional(readOnly = true)
    public PublicPageResponse<LabNewsArticleResponse> listAdmin(int page, int size) {
        if (page < 0) throw badRequest("Page must not be negative");
        if (size < 1 || size > 100) throw badRequest("Size must be between 1 and 100");
        return PublicPageResponse.from(articleRepository.findActiveForAdmin(PageRequest.of(page, size)).map(this::toResponse));
    }

    @Override
    @Transactional
    public LabNewsArticleResponse create(CreateLabNewsArticleRequest request) {
        Instant now = Instant.now();
        LabNewsArticleEntity article = LabNewsArticleEntity.create(
                required(request.getTitle(), "Title is required"), optional(request.getExcerpt()),
                required(request.getSourceName(), "Source name is required"), requiredHttpUrl(request.getSourceUrl()),
                requiredPublishedAt(request.getPublishedAt()), Boolean.TRUE.equals(request.getIsPublic()), now);
        return toResponse(articleRepository.saveAndFlush(article));
    }

    @Override
    @Transactional
    public LabNewsArticleResponse update(Long id, UpdateLabNewsArticleRequest request) {
        LabNewsArticleEntity article = findActive(id);
        article.update(
                request.isTitlePresent() ? required(request.getTitle(), "Title is required") : article.getTitle(),
                request.isExcerptPresent() ? optional(request.getExcerpt()) : article.getExcerpt(),
                request.isSourceNamePresent() ? required(request.getSourceName(), "Source name is required") : article.getSourceName(),
                request.isSourceUrlPresent() ? requiredHttpUrl(request.getSourceUrl()) : article.getSourceUrl(),
                request.isPublishedAtPresent() ? requiredPublishedAt(request.getPublishedAt()) : article.getPublishedAt(),
                request.getIsPublic() == null ? article.getIsPublic() : request.getIsPublic(), Instant.now());
        return toResponse(articleRepository.saveAndFlush(article));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        LabNewsArticleEntity article = findActive(id);
        article.softDelete(Instant.now());
        articleRepository.saveAndFlush(article);
    }

    private LabNewsArticleEntity findActive(Long id) {
        return articleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "News article not found"));
    }

    private Instant requiredPublishedAt(Instant value) {
        if (value == null) throw badRequest("Published at is required");
        return value;
    }

    private String required(String value, String message) {
        String normalized = optional(value);
        if (normalized == null) throw badRequest(message);
        return normalized;
    }

    private String optional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String requiredHttpUrl(String value) {
        String normalized = required(value, "Source URL is required");
        try {
            URI uri = URI.create(normalized);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                throw badRequest("Source URL must be an HTTP(S) URL with a valid host");
            }
        } catch (IllegalArgumentException exception) {
            throw badRequest("Source URL must be an HTTP(S) URL with a valid host");
        }
        return normalized;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private Specification<LabNewsArticleEntity> publicArchiveSpecification(String query, String source, Integer year) {
        return (root, queryObject, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isNull(root.get("deletedAt")));
            predicates.add(builder.isTrue(root.get("isPublic")));
            if (query != null) {
                String pattern = "%" + query.toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(builder.coalesce(root.get("excerpt"), "")), pattern)
                ));
            }
            if (source != null) {
                predicates.add(builder.equal(builder.lower(builder.trim(root.get("sourceName"))), source.toLowerCase(Locale.ROOT)));
            }
            if (year != null) {
                Instant start = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
                Instant end = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
                predicates.add(builder.greaterThanOrEqualTo(root.get("publishedAt"), start));
                predicates.add(builder.lessThan(root.get("publishedAt"), end));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private LabNewsArticleResponse toResponse(LabNewsArticleEntity article) {
        return new LabNewsArticleResponse(article.getId(), article.getTitle(), article.getExcerpt(), article.getSourceName(),
                article.getSourceUrl(), article.getPublishedAt(), article.getIsPublic(), article.getCreatedAt(),
                article.getUpdatedAt(), article.getDeletedAt());
    }
}
