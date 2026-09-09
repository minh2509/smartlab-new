package com.smartlab.service.impl;

import com.smartlab.dto.response.PublicDocumentSummaryResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.entity.DocumentEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.enums.PublicDocumentFileType;
import com.smartlab.enums.PublicDocumentSort;
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

import java.time.Year;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PublicDocumentServiceImpl implements PublicDocumentService {
    private static final int MAX_PAGE_SIZE = 48;

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;

    @Override
    @Transactional(readOnly = true)
    public PublicPageResponse<PublicDocumentSummaryResponse> list(
            String query,
            Long projectId,
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
        Specification<DocumentEntity> specification = publicSpecification(normalizedQuery, projectId, normalizedType, year);
        return PublicPageResponse.from(documentRepository.findAll(specification, PageRequest.of(page, size, documentSort))
                .map(this::toSummary));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> years() {
        return documentRepository.findPublicYears();
    }

    private Specification<DocumentEntity> publicSpecification(
            String query,
            Long projectId,
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
            if (year != null) {
                Instant yearStart = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
                Instant nextYearStart = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
                predicates.add(builder.greaterThanOrEqualTo(root.get("updatedAt"), yearStart));
                predicates.add(builder.lessThan(root.get("updatedAt"), nextYearStart));
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
            case OLDEST -> Sort.by(Sort.Order.asc("updatedAt"), Sort.Order.asc("id"));
            case TITLE_ASC -> Sort.by(Sort.Order.asc("title").ignoreCase(), Sort.Order.asc("id"));
            case TITLE_DESC -> Sort.by(Sort.Order.desc("title").ignoreCase(), Sort.Order.desc("id"));
            case LATEST -> Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
        };
    }

    private PublicDocumentSummaryResponse toSummary(DocumentEntity document) {
        StoredFileEntity file = document.getCurrentFile();
        return new PublicDocumentSummaryResponse(
                document.getId(), document.getTitle(), document.getDescription(),
                document.getProject().getId(), document.getProject().getCode(), document.getProject().getName(),
                file.getId(), file.getOriginalName(), file.getMimeType(), file.getSizeBytes(),
                documentVersionRepository.findMaxVersionNo(document.getId()), document.getUpdatedAt()
        );
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
