package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateLabNewsArticleRequest;
import com.smartlab.dto.request.UpdateLabNewsArticleRequest;
import com.smartlab.dto.response.LabNewsArticleResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.entity.LabNewsArticleEntity;
import com.smartlab.repo.LabNewsArticleRepository;
import com.smartlab.service.LabNewsArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.List;

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

    private LabNewsArticleResponse toResponse(LabNewsArticleEntity article) {
        return new LabNewsArticleResponse(article.getId(), article.getTitle(), article.getExcerpt(), article.getSourceName(),
                article.getSourceUrl(), article.getPublishedAt(), article.getIsPublic(), article.getCreatedAt(),
                article.getUpdatedAt(), article.getDeletedAt());
    }
}
