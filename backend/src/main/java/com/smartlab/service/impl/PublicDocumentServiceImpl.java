package com.smartlab.service.impl;

import com.smartlab.dto.response.PublicDocumentCategoryResponse;
import com.smartlab.dto.response.PublicDocumentSummaryResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.entity.DocumentCategoryEntity;
import com.smartlab.entity.DocumentEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.enums.PublicDocumentFileType;
import com.smartlab.enums.PublicDocumentSort;
import com.smartlab.repo.DocumentCategoryRepository;
import com.smartlab.repo.DocumentRepository;
import com.smartlab.repo.DocumentVersionRepository;
import com.smartlab.service.PublicDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicDocumentServiceImpl implements PublicDocumentService {
    private static final int MAX_PAGE_SIZE = 48;

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentCategoryRepository documentCategoryRepository;

    @Override
    @Transactional(readOnly = true)
    public PublicPageResponse<PublicDocumentSummaryResponse> list(
            String query,
            Long projectId,
            String category,
            PublicDocumentFileType fileType,
            Integer year,
            PublicDocumentSort sort,
            int page,
            int size
    ) {
        if (page < 0) throw badRequest("Page must not be negative");
        if (size < 1 || size > MAX_PAGE_SIZE) throw badRequest("Size must be between 1 and " + MAX_PAGE_SIZE);
        if (projectId != null && projectId <= 0) throw badRequest("Project id must be positive");
        if (year != null && (year < 2000 || year > Year.now().getValue())) throw badRequest("Year is invalid");
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        PublicDocumentFileType normalizedType = fileType == null ? PublicDocumentFileType.ALL : fileType;
        PublicDocumentSort normalizedSort = sort == null ? PublicDocumentSort.LATEST : sort;
        Sort documentSort = sortFor(normalizedSort);
        Specification<DocumentEntity> specification = publicSpecification(normalizedQuery, projectId, category, normalizedType, year);
        var documents = documentRepository.findAll(specification, PageRequest.of(page, size, documentSort));
        Map<Long, Integer> versionNumbers = versionNumbers(documents.getContent().stream().map(DocumentEntity::getId).toList());
        return new PublicPageResponse<>(documents.getContent().stream()
                .map(document -> toSummary(document, versionNumbers.getOrDefault(document.getId(), 0)))
                .toList(), documents.getNumber(), documents.getSize(), documents.getTotalElements(), documents.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> years() {
        return documentRepository.findPublicYears();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicDocumentCategoryResponse> categories(Integer year) {
        if (year != null && (year < 2000 || year > Year.now().getValue())) {
            throw badRequest("Year is invalid");
        }
        List<DocumentCategoryEntity> activeCategories = documentCategoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAscNameAscIdAsc();
        Map<Long, Long> counts = documentCategoryRepository.countPublicDocumentsByCategory(year).stream()
                .collect(Collectors.toMap(
                        DocumentCategoryRepository.CategoryCountProjection::getCategoryId,
                        DocumentCategoryRepository.CategoryCountProjection::getDocumentCount
                ));
        return activeCategories.stream()
                .map(c -> new PublicDocumentCategoryResponse(
                        c.getId(),
                        c.getCode(),
                        c.getName(),
                        c.getDescription(),
                        c.getDisplayOrder(),
                        counts.getOrDefault(c.getId(), 0L)
                ))
                .toList();
    }

    private Specification<DocumentEntity> publicSpecification(
            String query,
            Long projectId,
            String category,
            PublicDocumentFileType fileType,
            Integer year
    ) {
        return (root, criteriaQuery, builder) -> {
            var file = root.join("currentFile");
            var project = root.join("project");
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isNull(root.get("deletedAt")));
            predicates.add(builder.isNull(file.get("deletedAt")));
            predicates.add(builder.equal(file.get("accessScope"), "PUBLIC"));
            predicates.add(builder.isNull(project.get("deletedAt")));
            predicates.add(builder.isTrue(project.get("isPublic")));
            if (!query.isEmpty()) {
                String pattern = "%" + query + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern),
                        builder.like(builder.lower(file.get("originalName")), pattern)
                ));
            }
            if (projectId != null) predicates.add(builder.equal(project.get("id"), projectId));
            if (category != null && !category.isBlank() && !category.equalsIgnoreCase("all") && !category.equalsIgnoreCase("tat-ca")) {
                var categoryJoin = root.join("category");
                predicates.add(builder.isTrue(categoryJoin.get("isActive")));
                String trimmed = category.trim();
                try {
                    Long catId = Long.parseLong(trimmed);
                    predicates.add(builder.equal(categoryJoin.get("id"), catId));
                } catch (NumberFormatException e) {
                    predicates.add(builder.equal(builder.lower(categoryJoin.get("code")), trimmed.toLowerCase(Locale.ROOT)));
                }
            }
            if (year != null) {
                Instant yearStart = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
                Instant nextYearStart = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
                predicates.add(builder.greaterThanOrEqualTo(root.get("archiveDate"), yearStart));
                predicates.add(builder.lessThan(root.get("archiveDate"), nextYearStart));
            }
            addFileTypePredicate(predicates, fileType, file, builder);
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private void addFileTypePredicate(
            List<jakarta.persistence.criteria.Predicate> predicates,
            PublicDocumentFileType fileType,
            jakarta.persistence.criteria.From<?, ?> file,
            jakarta.persistence.criteria.CriteriaBuilder builder
    ) {
        if (fileType == PublicDocumentFileType.ALL) return;
        var mime = builder.lower(file.get("mimeType"));
        var name = builder.lower(file.get("originalName"));
        List<jakarta.persistence.criteria.Predicate> matches = switch (fileType) {
            case PDF -> List.of(builder.equal(mime, "application/pdf"));
            case DOCUMENT -> List.of(
                    builder.like(mime, "text/%"),
                    builder.equal(mime, "application/msword"),
                    builder.equal(mime, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                    builder.like(name, "%.doc"),
                    builder.like(name, "%.docx")
            );
            case SPREADSHEET -> List.of(
                    builder.equal(mime, "text/csv"),
                    builder.equal(mime, "application/vnd.ms-excel"),
                    builder.equal(mime, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                    builder.like(name, "%.xls"),
                    builder.like(name, "%.xlsx"),
                    builder.like(name, "%.csv")
            );
            case PRESENTATION -> List.of(
                    builder.equal(mime, "application/vnd.ms-powerpoint"),
                    builder.equal(mime, "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
                    builder.like(name, "%.ppt"),
                    builder.like(name, "%.pptx")
            );
            case IMAGE -> List.of(builder.like(mime, "image/%"));
            case ARCHIVE -> List.of(
                    builder.equal(mime, "application/zip"),
                    builder.equal(mime, "application/x-zip-compressed"),
                    builder.equal(mime, "application/x-rar-compressed"),
                    builder.equal(mime, "application/x-7z-compressed"),
                    builder.like(name, "%.zip"),
                    builder.like(name, "%.rar"),
                    builder.like(name, "%.7z")
            );
            case OTHER -> List.of(
                    builder.and(builder.notEqual(mime, "application/pdf"), builder.notLike(mime, "text/%"),
                            builder.notLike(mime, "image/%"), builder.notEqual(mime, "application/msword"),
                            builder.notEqual(mime, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                            builder.notEqual(mime, "text/csv"), builder.notEqual(mime, "application/vnd.ms-excel"),
                            builder.notEqual(mime, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                            builder.notEqual(mime, "application/vnd.ms-powerpoint"),
                            builder.notEqual(mime, "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
                            builder.notLike(name, "%.doc"), builder.notLike(name, "%.docx"),
                            builder.notLike(name, "%.xls"), builder.notLike(name, "%.xlsx"), builder.notLike(name, "%.csv"),
                            builder.notLike(name, "%.ppt"), builder.notLike(name, "%.pptx"),
                            builder.notLike(name, "%.zip"), builder.notLike(name, "%.rar"), builder.notLike(name, "%.7z"),
                            builder.notEqual(mime, "application/zip"),
                            builder.notEqual(mime, "application/x-zip-compressed"), builder.notEqual(mime, "application/x-rar-compressed"),
                            builder.notEqual(mime, "application/x-7z-compressed"))
            );
            case ALL -> List.of();
        };
        predicates.add(builder.or(matches.toArray(jakarta.persistence.criteria.Predicate[]::new)));
    }

    private Sort sortFor(PublicDocumentSort sort) {
        return switch (sort) {
            case OLDEST -> Sort.by(Sort.Order.asc("archiveDate"), Sort.Order.asc("id"));
            case TITLE_ASC -> Sort.by(Sort.Order.asc("title").ignoreCase(), Sort.Order.asc("id"));
            case TITLE_DESC -> Sort.by(Sort.Order.desc("title").ignoreCase(), Sort.Order.desc("id"));
            case LATEST -> Sort.by(Sort.Order.desc("archiveDate"), Sort.Order.desc("id"));
        };
    }

    private PublicDocumentSummaryResponse toSummary(DocumentEntity document, int currentVersionNo) {
        StoredFileEntity file = document.getCurrentFile();
        DocumentCategoryEntity category = document.getCategory();
        boolean categoryIsPublic = category != null && Boolean.TRUE.equals(category.getIsActive());
        return new PublicDocumentSummaryResponse(
                document.getId(),
                document.getTitle(),
                document.getDescription(),
                document.getProject().getId(),
                document.getProject().getCode(),
                document.getProject().getName(),
                categoryIsPublic ? category.getId() : null,
                categoryIsPublic ? category.getCode() : null,
                categoryIsPublic ? category.getName() : null,
                file.getId(),
                file.getOriginalName(),
                file.getMimeType(),
                file.getSizeBytes(),
                currentVersionNo,
                document.getArchiveDate(),
                document.getUpdatedAt()
        );
    }

    private Map<Long, Integer> versionNumbers(List<Long> documentIds) {
        if (documentIds.isEmpty()) return Map.of();
        return java.util.Optional.ofNullable(documentVersionRepository.findMaxVersionNosByDocumentIds(documentIds))
                .orElseGet(List::of)
                .stream()
                .collect(Collectors.toMap(
                        DocumentVersionRepository.VersionNumberProjection::getDocumentId,
                        projection -> projection.getMaxVersionNo() == null ? 0 : projection.getMaxVersionNo()
                ));
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
