package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateGalleryItemRequest;
import com.smartlab.dto.request.UpdateGalleryItemRequest;
import com.smartlab.dto.response.AdminGalleryItemResponse;
import com.smartlab.dto.response.PublicGalleryItemResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.entity.EventEntity;
import com.smartlab.entity.GalleryItemEntity;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.enums.EventVisibility;
import com.smartlab.enums.GalleryCategory;
import com.smartlab.enums.GalleryItemStatus;
import com.smartlab.enums.PublicGallerySort;
import com.smartlab.repo.EventRepository;
import com.smartlab.repo.GalleryItemRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.StoredFileRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.FileService;
import com.smartlab.service.GalleryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.criteria.JoinType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GalleryServiceImpl implements GalleryService {
    private static final int DEFAULT_PUBLIC_SIZE = 24;
    private static final int MAX_PUBLIC_SIZE = 48;
    private static final int MAX_ADMIN_SIZE = 100;

    private final GalleryItemRepository galleryItemRepository;
    private final StoredFileRepository storedFileRepository;
    private final ProjectRepository projectRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final FileService fileService;

    @Override
    @Transactional(readOnly = true)
    public PublicPageResponse<PublicGalleryItemResponse> listPublic(String query, GalleryCategory category, Long projectId,
                                                                    Integer year, PublicGallerySort sort, int page, int size) {
        validateFilters(projectId, year, query);
        int safePage = Math.max(0, page);
        int safeSize = clampSize(size, DEFAULT_PUBLIC_SIZE, MAX_PUBLIC_SIZE);
        String normalizedQuery = normalizeQuery(query);
        Specification<GalleryItemEntity> spec = publicSpec(normalizedQuery, category, projectId, year);
        Sort ordering = sort == PublicGallerySort.OLDEST
                ? JpaSort.unsafe(Sort.Direction.ASC, "coalesce(capturedAt, publishedAt)").and(Sort.by(Sort.Direction.ASC, "id"))
                : JpaSort.unsafe(Sort.Direction.DESC, "coalesce(capturedAt, publishedAt)").and(Sort.by(Sort.Direction.DESC, "id"));
        return PublicPageResponse.from(galleryItemRepository.findAll(spec, PageRequest.of(safePage, safeSize, ordering))
                .map(this::toPublicResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> listPublicYears() {
        return galleryItemRepository.findPublicYears();
    }

    @Override
    @Transactional(readOnly = true)
    public PublicPageResponse<AdminGalleryItemResponse> listAdmin(String query, GalleryItemStatus status,
                                                                  GalleryCategory category, Long projectId, Integer year,
                                                                  PublicGallerySort sort, int page, int size) {
        validateFilters(projectId, year, query);
        int safePage = Math.max(0, page);
        int safeSize = clampSize(size, 24, MAX_ADMIN_SIZE);
        Specification<GalleryItemEntity> spec = adminSpec(normalizeQuery(query), status, category, projectId, year);
        Sort ordering = sort == PublicGallerySort.OLDEST
                ? Sort.by(Sort.Direction.ASC, "updatedAt", "id")
                : Sort.by(Sort.Direction.DESC, "updatedAt", "id");
        return PublicPageResponse.from(galleryItemRepository.findAll(spec, PageRequest.of(safePage, safeSize, ordering))
                .map(this::toAdminResponse));
    }

    @Override
    @Transactional
    public AdminGalleryItemResponse create(CreateGalleryItemRequest request, MultipartFile file, String email) {
        if (file == null || file.isEmpty()) throw badRequest("An image file is required");
        String title = required(request.getTitle(), "Gallery title is required");
        String altText = required(request.getAltText(), "Gallery alt text is required");
        ProjectEntity project = activeProject(request.getProjectId());
        EventEntity event = activeEvent(request.getEventId());
        var uploaded = fileService.upload(file, "PRIVATE", title, email);
        StoredFileEntity stored = storedFileRepository.findByIdAndDeletedAtIsNull(uploaded.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Uploaded file is unavailable"));
        if (!isSupportedImage(stored.getMimeType())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Gallery files must be images");
        }
        if (galleryItemRepository.existsByFile_IdAndDeletedAtIsNull(stored.getId())) {
            throw conflict("File is already owned by a gallery item");
        }
        GalleryItemEntity item = GalleryItemEntity.draft(stored, title, trimNullable(request.getCaption()), altText,
                request.getCategory(), project, event, request.getCapturedAt(), request.getIsFeatured(),
                userRepository.findByEmail(email).orElse(null));
        return toAdminResponse(galleryItemRepository.save(item));
    }

    @Override
    @Transactional
    public AdminGalleryItemResponse update(Long id, UpdateGalleryItemRequest request) {
        GalleryItemEntity item = activeForUpdate(id);
        assertConsistent(item);
        ProjectEntity project = activeProject(request.getProjectId());
        EventEntity event = activeEvent(request.getEventId());
        if (item.getStatus() == GalleryItemStatus.PUBLISHED) {
            ensurePublicRelation(project, event);
            if (!"PUBLIC".equalsIgnoreCase(activeFile(item).getAccessScope())) {
                throw conflict("Published gallery item must use a PUBLIC file");
            }
        }
        item.updateMetadata(required(request.getTitle(), "Gallery title is required"), trimNullable(request.getCaption()),
                required(request.getAltText(), "Gallery alt text is required"), request.getCategory(), project, event,
                request.getCapturedAt(), request.getIsFeatured());
        return toAdminResponse(item);
    }

    @Override
    @Transactional
    public AdminGalleryItemResponse publish(Long id) {
        GalleryItemEntity item = activeForUpdate(id);
        StoredFileEntity file = activeFile(item);
        required(item.getAltText(), "Gallery alt text is required");
        ensurePublicRelation(item);
        if (item.getStatus() != GalleryItemStatus.DRAFT || !"PRIVATE".equalsIgnoreCase(file.getAccessScope())) {
            throw conflict("Gallery draft file must be PRIVATE before publishing");
        }
        file.setAccessScope("PUBLIC");
        item.publish(Instant.now());
        return toAdminResponse(item);
    }

    @Override
    @Transactional
    public AdminGalleryItemResponse unpublish(Long id) {
        GalleryItemEntity item = activeForUpdate(id);
        StoredFileEntity file = activeFile(item);
        if (item.getStatus() == GalleryItemStatus.PUBLISHED && !"PUBLIC".equalsIgnoreCase(file.getAccessScope())) {
            throw conflict("Published gallery item has an inconsistent file scope");
        }
        if (item.getStatus() == GalleryItemStatus.DRAFT && !"PRIVATE".equalsIgnoreCase(file.getAccessScope())) {
            throw conflict("Draft gallery item has an inconsistent file scope");
        }
        file.setAccessScope("PRIVATE");
        item.unpublish();
        return toAdminResponse(item);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        GalleryItemEntity item = activeForUpdate(id);
        StoredFileEntity file = activeFile(item);
        if ((item.getStatus() == GalleryItemStatus.PUBLISHED && !"PUBLIC".equalsIgnoreCase(file.getAccessScope()))
                || (item.getStatus() == GalleryItemStatus.DRAFT && !"PRIVATE".equalsIgnoreCase(file.getAccessScope()))) {
            throw conflict("Gallery item and file scope are inconsistent");
        }
        if ("PUBLIC".equalsIgnoreCase(file.getAccessScope())) file.setAccessScope("PRIVATE");
        item.softDelete(Instant.now());
    }

    private Specification<GalleryItemEntity> publicSpec(String query, GalleryCategory category, Long projectId, Integer year) {
        return (root, cq, cb) -> {
            var file = root.join("file", JoinType.INNER);
            var project = root.join("project", JoinType.LEFT);
            var event = root.join("event", JoinType.LEFT);
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(cb.equal(root.get("status"), GalleryItemStatus.PUBLISHED));
            predicates.add(cb.isNull(file.get("deletedAt")));
            predicates.add(cb.equal(file.get("accessScope"), "PUBLIC"));
            predicates.add(cb.isNotNull(root.get("publishedAt")));
            predicates.add(cb.or(cb.isNull(project.get("id")), cb.and(cb.isNull(project.get("deletedAt")), cb.isTrue(project.get("isPublic")))));
            if (query != null) {
                String pattern = "%" + query.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("title")), pattern), cb.like(cb.lower(cb.coalesce(root.get("caption"), "")), pattern)));
            }
            if (category != null) predicates.add(cb.equal(root.get("category"), category));
            if (projectId != null) predicates.add(cb.equal(project.get("id"), projectId));
            if (year != null) predicates.add(yearPredicate(root, cb, year));
            // Events are safe to expose only when they are explicitly public and active.
            predicates.add(cb.or(cb.isNull(event.get("id")), cb.and(cb.isNull(event.get("deletedAt")), cb.equal(event.get("visibility"), EventVisibility.PUBLIC))));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private Specification<GalleryItemEntity> adminSpec(String query, GalleryItemStatus status, GalleryCategory category, Long projectId, Integer year) {
        return (root, cq, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (query != null) {
                String pattern = "%" + query.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("title")), pattern), cb.like(cb.lower(cb.coalesce(root.get("caption"), "")), pattern), cb.like(cb.lower(root.join("file").get("originalName")), pattern)));
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (category != null) predicates.add(cb.equal(root.get("category"), category));
            if (projectId != null) predicates.add(cb.equal(root.join("project").get("id"), projectId));
            if (year != null) predicates.add(yearPredicate(root, cb, year));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private jakarta.persistence.criteria.Predicate yearPredicate(jakarta.persistence.criteria.Root<GalleryItemEntity> root,
                                                                  jakarta.persistence.criteria.CriteriaBuilder cb, int year) {
        Instant start = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();
        Instant end = ZonedDateTime.of(year + 1, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();
        return cb.or(
                cb.and(cb.greaterThanOrEqualTo(root.get("capturedAt"), start), cb.lessThan(root.get("capturedAt"), end)),
                cb.and(cb.isNull(root.get("capturedAt")), cb.greaterThanOrEqualTo(root.get("publishedAt"), start), cb.lessThan(root.get("publishedAt"), end))
        );
    }

    private void ensurePublicRelation(GalleryItemEntity item) {
        ensurePublicRelation(item.getProject(), item.getEvent());
    }

    private void ensurePublicRelation(ProjectEntity project, EventEntity event) {
        if (project != null && (project.getDeletedAt() != null || !Boolean.TRUE.equals(project.getIsPublic()))) {
            throw conflict("Gallery project must be active and public before publishing");
        }
        if (event != null && (event.getDeletedAt() != null || event.getVisibility() != EventVisibility.PUBLIC)) {
            throw conflict("Gallery event must be active and public before publishing");
        }
    }

    private StoredFileEntity activeFile(GalleryItemEntity item) {
        StoredFileEntity file = item.getFile();
        if (file == null || file.getDeletedAt() != null) throw conflict("Gallery file is missing or deleted");
        if (!isSupportedImage(file.getMimeType()) || file.getProjectId() != null) {
            throw conflict("Gallery file is not a dedicated image");
        }
        return file;
    }

    private void assertConsistent(GalleryItemEntity item) {
        StoredFileEntity file = activeFile(item);
        boolean publicScope = "PUBLIC".equalsIgnoreCase(file.getAccessScope());
        if ((item.getStatus() == GalleryItemStatus.PUBLISHED && !publicScope)
                || (item.getStatus() == GalleryItemStatus.DRAFT && publicScope)) {
            throw conflict("Gallery item and file scope are inconsistent");
        }
    }

    private ProjectEntity activeProject(Long id) {
        if (id == null) return null;
        if (id <= 0) throw badRequest("Project id must be positive");
        return projectRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> notFound("Project not found"));
    }

    private EventEntity activeEvent(Long id) {
        if (id == null) return null;
        if (id <= 0) throw badRequest("Event id must be positive");
        return eventRepository.findActiveById(id).orElseThrow(() -> notFound("Event not found"));
    }

    private GalleryItemEntity activeForUpdate(Long id) {
        if (id == null || id <= 0) throw badRequest("Gallery id must be positive");
        return galleryItemRepository.findActiveByIdForUpdate(id).orElseThrow(() -> notFound("Gallery item not found"));
    }

    private PublicGalleryItemResponse toPublicResponse(GalleryItemEntity item) {
        StoredFileEntity file = item.getFile();
        ProjectEntity project = item.getProject();
        EventEntity event = item.getEvent();
        return new PublicGalleryItemResponse(item.getId(), item.getTitle(), item.getCaption(), item.getAltText(), item.getCategory(),
                project == null ? null : project.getId(), project == null ? null : project.getCode(), project == null ? null : project.getName(),
                event == null ? null : event.getId(), event == null ? null : event.getTitle(), file.getId(), file.getOriginalName(), file.getMimeType(),
                file.getSizeBytes(), item.getCapturedAt(), item.getPublishedAt(), item.getIsFeatured());
    }

    private AdminGalleryItemResponse toAdminResponse(GalleryItemEntity item) {
        StoredFileEntity file = item.getFile();
        ProjectEntity project = item.getProject();
        EventEntity event = item.getEvent();
        return new AdminGalleryItemResponse(item.getId(), item.getTitle(), item.getCaption(), item.getAltText(), item.getCategory(),
                project == null ? null : project.getId(), project == null ? null : project.getName(), event == null ? null : event.getId(),
                event == null ? null : event.getTitle(), file == null ? null : file.getId(), file == null ? null : file.getOriginalName(),
                file == null ? null : file.getMimeType(), file == null ? null : file.getSizeBytes(), item.getCapturedAt(), item.getStatus(),
                item.getIsFeatured(), item.getPublishedAt(), item.getUpdatedAt());
    }

    private boolean isSupportedImage(String mime) { return mime != null && (mime.equalsIgnoreCase("image/jpeg") || mime.equalsIgnoreCase("image/png") || mime.equalsIgnoreCase("image/webp") || mime.equalsIgnoreCase("image/gif")); }
    private String required(String value, String message) { if (value == null || value.isBlank()) throw badRequest(message); return value.trim(); }
    private String trimNullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String normalizeQuery(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private void validateFilters(Long projectId, Integer year, String query) {
        if (projectId != null && projectId <= 0) throw badRequest("Project id must be positive");
        if (year != null && (year < 1900 || year > 2100)) throw badRequest("Year must be between 1900 and 2100");
        if (query != null && query.trim().length() > 200) throw badRequest("Query must be at most 200 characters");
    }
    private int clampSize(int size, int fallback, int max) { return size < 1 ? fallback : Math.min(size, max); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
