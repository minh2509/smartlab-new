package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateLabArticleRequest;
import com.smartlab.dto.request.UpdateLabArticleRequest;
import com.smartlab.dto.response.AdminLabArticleResponse;
import com.smartlab.dto.response.PublicLabArticleDetailResponse;
import com.smartlab.dto.response.PublicLabArticleSummaryResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.entity.LabArticleEntity;
import com.smartlab.enums.LabArticleStatus;
import com.smartlab.repo.LabArticleRepository;
import com.smartlab.service.LabArticleService;
import com.smartlab.service.LabArticleSlugGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LabArticleServiceImpl implements LabArticleService {
    private final LabArticleRepository articleRepository;
    private final LabArticleSlugGenerator slugGenerator;

    @Override @Transactional(readOnly = true)
    public List<PublicLabArticleSummaryResponse> listLatest(int limit) {
        validateRange(limit, 1, 12, "Limit must be between 1 and 12");
        return articleRepository.findNewestPublished(PageRequest.of(0, limit)).stream().map(this::toSummary).toList();
    }

    @Override @Transactional(readOnly = true)
    public PublicPageResponse<PublicLabArticleSummaryResponse> listArchive(int page, int size) {
        validatePage(page, size, 48);
        return PublicPageResponse.from(articleRepository.findPublishedArchive(PageRequest.of(page, size)).map(this::toSummary));
    }

    @Override @Transactional(readOnly = true)
    public PublicLabArticleDetailResponse getPublicBySlug(String slug) {
        LabArticleEntity article = articleRepository.findBySlugAndStatusAndDeletedAtIsNull(slug, LabArticleStatus.PUBLISHED)
                .orElseThrow(() -> notFound("Article not found"));
        return new PublicLabArticleDetailResponse(article.getId(), article.getTitle(), article.getSlug(), article.getExcerpt(),
                article.getContent(), article.getPublishedAt());
    }

    @Override @Transactional(readOnly = true)
    public PublicPageResponse<AdminLabArticleResponse> listAdmin(int page, int size) {
        validatePage(page, size, 100);
        return PublicPageResponse.from(articleRepository.findActiveForAdmin(PageRequest.of(page, size)).map(this::toAdmin));
    }

    @Override @Transactional
    public AdminLabArticleResponse create(CreateLabArticleRequest request) {
        String title = required(request.getTitle(), "Title is required");
        Map<String, Object> content = requiredContent(request.getContent());
        String requestedSlug = optional(request.getSlug());
        String slug = requestedSlug == null ? slugGenerator.generateUniqueSlug(title) : slugGenerator.normalize(requestedSlug);
        if (requestedSlug != null && articleRepository.existsBySlugAndDeletedAtIsNull(slug)) throw conflict("Article slug already exists");
        LabArticleStatus status = request.getStatus() == null ? LabArticleStatus.DRAFT : request.getStatus();
        Instant now = Instant.now();
        Instant publishedAt = resolveCreatePublishedAt(status, request.getPublishedAt(), now);
        return toAdmin(articleRepository.saveAndFlush(LabArticleEntity.create(title, slug, optional(request.getExcerpt()), content,
                status, publishedAt, now)));
    }

    @Override @Transactional
    public AdminLabArticleResponse update(Long id, UpdateLabArticleRequest request) {
        LabArticleEntity article = findActive(id);
        String title = request.hasTitle() ? required(request.getTitle(), "Title is required") : article.getTitle();
        Map<String, Object> content = request.hasContent() ? requiredContent(request.getContent()) : article.getContent();
        String slug = article.getSlug();
        if (request.hasSlug()) {
            slug = slugGenerator.normalize(request.getSlug());
            if (!slug.equals(article.getSlug()) && articleRepository.existsBySlugAndDeletedAtIsNull(slug)) throw conflict("Article slug already exists");
        }
        String excerpt = request.hasExcerpt() ? optional(request.getExcerpt()) : article.getExcerpt();
        LabArticleStatus status = request.hasStatus() ? request.getStatus() : article.getStatus();
        Instant publishedAt = request.hasPublishedAt() ? request.getPublishedAt() : article.getPublishedAt();
        if (status == LabArticleStatus.PUBLISHED && publishedAt == null) publishedAt = Instant.now();
        article.update(title, slug, excerpt, content, status, publishedAt, Instant.now());
        return toAdmin(articleRepository.saveAndFlush(article));
    }

    @Override @Transactional
    public void delete(Long id) {
        LabArticleEntity article = findActive(id);
        article.softDelete(Instant.now());
        articleRepository.saveAndFlush(article);
    }

    private LabArticleEntity findActive(Long id) {
        return articleRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> notFound("Article not found"));
    }

    private Instant resolveCreatePublishedAt(LabArticleStatus status, Instant requested, Instant now) {
        if (status == LabArticleStatus.PUBLISHED) return requested == null ? now : requested;
        return null;
    }

    private void validatePage(int page, int size, int maxSize) {
        if (page < 0) throw badRequest("Page must not be negative");
        validateRange(size, 1, maxSize, "Size must be between 1 and " + maxSize);
    }

    private void validateRange(int value, int min, int max, String message) { if (value < min || value > max) throw badRequest(message); }
    private Map<String, Object> requiredContent(Map<String, Object> value) { if (value == null) throw badRequest("Content is required"); return value; }
    private String required(String value, String message) { String normalized = optional(value); if (normalized == null) throw badRequest(message); return normalized; }
    private String optional(String value) { if (value == null) return null; String normalized = value.trim(); return normalized.isEmpty() ? null : normalized; }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private PublicLabArticleSummaryResponse toSummary(LabArticleEntity article) { return new PublicLabArticleSummaryResponse(article.getId(), article.getTitle(), article.getSlug(), article.getExcerpt(), article.getPublishedAt()); }
    private AdminLabArticleResponse toAdmin(LabArticleEntity article) { return new AdminLabArticleResponse(article.getId(), article.getTitle(), article.getSlug(), article.getExcerpt(), article.getContent(), article.getStatus(), article.getPublishedAt(), article.getCreatedAt(), article.getUpdatedAt()); }
}
