package com.smartlab.service.impl;

import com.smartlab.dto.request.AssignDocumentCategoryRequest;
import com.smartlab.dto.request.CreateDocumentCategoryRequest;
import com.smartlab.dto.request.ReorderDocumentCategoryRequest;
import com.smartlab.dto.request.UpdateDocumentCategoryRequest;
import com.smartlab.dto.response.AdminDocumentItemResponse;
import com.smartlab.dto.response.DocumentCategoryResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.entity.DocumentCategoryEntity;
import com.smartlab.entity.DocumentEntity;
import com.smartlab.repo.DocumentCategoryRepository;
import com.smartlab.repo.DocumentRepository;
import com.smartlab.service.AuditService;
import com.smartlab.service.DocumentCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentCategoryServiceImpl implements DocumentCategoryService {
    private static final int MAX_ASSIGNMENT_PAGE_SIZE = 100;
    private static final String TARGET_TYPE = "DOCUMENT_CATEGORY";
    private static final String ACTION_CREATED = "DOCUMENT_CATEGORY_CREATED";
    private static final String ACTION_UPDATED = "DOCUMENT_CATEGORY_UPDATED";
    private static final String ACTION_STATUS_TOGGLED = "DOCUMENT_CATEGORY_STATUS_TOGGLED";
    private static final String ACTION_REORDERED = "DOCUMENT_CATEGORY_REORDERED";
    private static final String ACTION_ASSIGNED = "DOCUMENT_CATEGORY_ASSIGNED";

    private final DocumentCategoryRepository documentCategoryRepository;
    private final DocumentRepository documentRepository;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<DocumentCategoryResponse> listCategories() {
        List<DocumentCategoryEntity> categories = documentCategoryRepository.findAllByOrderByDisplayOrderAscNameAscIdAsc();
        Map<Long, Long> counts = documentCategoryRepository.countTotalDocumentsByCategory().stream()
                .collect(Collectors.toMap(
                        DocumentCategoryRepository.CategoryCountProjection::getCategoryId,
                        DocumentCategoryRepository.CategoryCountProjection::getDocumentCount
                ));
        return categories.stream()
                .map(c -> toResponse(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public DocumentCategoryResponse createCategory(CreateDocumentCategoryRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request cannot be null");
        }
        String code = normalizeCode(request.code());
        String name = normalizeName(request.name());
        String description = normalizeDescription(request.description());

        if (documentCategoryRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category code already exists: " + code);
        }

        DocumentCategoryEntity entity = DocumentCategoryEntity.create(
                code,
                name,
                description,
                request.displayOrder() != null ? request.displayOrder() : 0,
                request.isActive() != null ? request.isActive() : true
        );
        DocumentCategoryEntity saved = documentCategoryRepository.saveAndFlush(entity);

        auditService.log(ACTION_CREATED, TARGET_TYPE, saved.getId().toString(), null, snapshot(saved));
        return toResponse(saved, 0L);
    }

    @Override
    @Transactional
    public DocumentCategoryResponse updateCategory(Long id, UpdateDocumentCategoryRequest request) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid category id");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request cannot be null");
        }
        DocumentCategoryEntity entity = documentCategoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + id));

        String name = normalizeName(request.name());
        String description = normalizeDescription(request.description());
        Map<String, Object> before = snapshot(entity);

        entity.update(name, description, request.displayOrder(), request.isActive());
        DocumentCategoryEntity saved = documentCategoryRepository.saveAndFlush(entity);

        auditService.log(ACTION_UPDATED, TARGET_TYPE, saved.getId().toString(), before, snapshot(saved));
        long count = documentCategoryRepository.countTotalDocumentsByCategory().stream()
                .filter(p -> p.getCategoryId().equals(saved.getId()))
                .mapToLong(DocumentCategoryRepository.CategoryCountProjection::getDocumentCount)
                .findFirst()
                .orElse(0L);
        return toResponse(saved, count);
    }

    @Override
    @Transactional
    public DocumentCategoryResponse toggleActive(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid category id");
        }
        DocumentCategoryEntity entity = documentCategoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + id));

        Map<String, Object> before = snapshot(entity);
        entity.setActive(!entity.getIsActive());
        DocumentCategoryEntity saved = documentCategoryRepository.saveAndFlush(entity);

        auditService.log(ACTION_STATUS_TOGGLED, TARGET_TYPE, saved.getId().toString(), before, snapshot(saved));
        long count = documentCategoryRepository.countTotalDocumentsByCategory().stream()
                .filter(p -> p.getCategoryId().equals(saved.getId()))
                .mapToLong(DocumentCategoryRepository.CategoryCountProjection::getDocumentCount)
                .findFirst()
                .orElse(0L);
        return toResponse(saved, count);
    }

    @Override
    @Transactional
    public void reorderCategories(ReorderDocumentCategoryRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Items cannot be empty");
        }
        Set<Long> ids = new LinkedHashSet<>();
        Set<Integer> orders = new LinkedHashSet<>();
        for (ReorderDocumentCategoryRequest.OrderItem item : request.items()) {
            if (item == null || item.id() == null || item.id() <= 0 || item.displayOrder() == null || item.displayOrder() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category IDs and display orders must be valid");
            }
            if (!ids.add(item.id())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category IDs must be unique");
            }
            if (!orders.add(item.displayOrder())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display orders must be unique");
            }
        }
        List<DocumentCategoryEntity> categories = documentCategoryRepository.findAllForUpdate();
        Set<Long> existingIds = categories.stream().map(DocumentCategoryEntity::getId).collect(Collectors.toSet());
        if (!existingIds.equals(ids)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reorder must include every category exactly once");
        }
        Map<Long, Integer> ordersById = request.items().stream()
                .collect(Collectors.toMap(ReorderDocumentCategoryRequest.OrderItem::id,
                        ReorderDocumentCategoryRequest.OrderItem::displayOrder));
        categories.forEach(category -> category.setDisplayOrder(ordersById.get(category.getId())));
        documentCategoryRepository.saveAllAndFlush(categories);
        auditService.log(ACTION_REORDERED, TARGET_TYPE, "BATCH", null, Map.of("count", request.items().size()));
    }

    @Override
    @Transactional
    public void assignDocuments(AssignDocumentCategoryRequest request) {
        if (request == null || request.documentIds() == null || request.documentIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document IDs must not be empty");
        }
        Set<Long> documentIds = new LinkedHashSet<>();
        for (Long documentId : request.documentIds()) {
            if (documentId == null || documentId <= 0 || !documentIds.add(documentId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document IDs must be positive and unique");
            }
        }
        DocumentCategoryEntity targetCategory = null;
        if (request.categoryId() != null) {
            targetCategory = documentCategoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + request.categoryId()));
            if (!Boolean.TRUE.equals(targetCategory.getIsActive())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target category is inactive");
            }
        }

        List<DocumentEntity> documents = documentRepository.findAllActiveByIdInForUpdate(documentIds);
        Set<Long> foundIds = documents.stream().map(DocumentEntity::getId).collect(Collectors.toSet());
        if (!foundIds.equals(documentIds)) {
            Long missingId = documentIds.stream().filter(id -> !foundIds.contains(id)).findFirst().orElse(null);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + missingId);
        }
        for (DocumentEntity document : documents) {
            document.assignCategory(targetCategory);
        }
        documentRepository.saveAllAndFlush(documents);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("categoryId", request.categoryId());
        after.put("documentCount", request.documentIds().size());
        after.put("documentIds", request.documentIds());
        auditService.log(ACTION_ASSIGNED, TARGET_TYPE, request.categoryId() != null ? request.categoryId().toString() : "UNASSIGNED", null, after);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminDocumentItemResponse> listDocumentsForAssignment() {
        return documentRepository.findAllActiveWithProjectAndFile().stream().map(this::toAssignmentItem).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PublicPageResponse<AdminDocumentItemResponse> listDocumentsForAssignment(
            String query, Long categoryId, String status, int page, int size
    ) {
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must not be negative");
        if (size < 1 || size > MAX_ASSIGNMENT_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size must be between 1 and " + MAX_ASSIGNMENT_PAGE_SIZE);
        }
        if (categoryId != null && categoryId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category id must be positive");
        }
        String normalizedStatus = status == null || status.isBlank() ? "ALL" : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "ASSIGNED", "UNASSIGNED").contains(normalizedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be ALL, ASSIGNED, or UNASSIGNED");
        }
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        var result = documentRepository.findAll(
                assignmentSpecification(normalizedQuery, categoryId, normalizedStatus),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")))
        ).map(this::toAssignmentItem);
        return PublicPageResponse.from(result);
    }

    private Specification<DocumentEntity> assignmentSpecification(String query, Long categoryId, String status) {
        return (root, criteriaQuery, builder) -> {
            var file = root.join("currentFile");
            var project = root.join("project");
            var category = root.join("category", jakarta.persistence.criteria.JoinType.LEFT);
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(builder.isNull(root.get("deletedAt")));
            predicates.add(builder.isNull(file.get("deletedAt")));
            predicates.add(builder.isNull(project.get("deletedAt")));
            if (!query.isEmpty()) {
                String pattern = "%" + query + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(project.get("code")), pattern),
                        builder.like(builder.lower(project.get("name")), pattern),
                        builder.like(builder.lower(file.get("originalName")), pattern)
                ));
            }
            if (categoryId != null) predicates.add(builder.equal(category.get("id"), categoryId));
            if ("ASSIGNED".equals(status)) predicates.add(builder.isNotNull(category.get("id")));
            if ("UNASSIGNED".equals(status)) predicates.add(builder.isNull(category.get("id")));
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private AdminDocumentItemResponse toAssignmentItem(DocumentEntity document) {
        return new AdminDocumentItemResponse(
                document.getId(), document.getTitle(), document.getProject().getId(), document.getProject().getCode(),
                document.getProject().getName(), document.getCategory() != null ? document.getCategory().getId() : null,
                document.getCategory() != null ? document.getCategory().getName() : null,
                document.getCurrentFile().getOriginalName(), document.getCurrentFile().getAccessScope(), document.getUpdatedAt()
        );
    }

    private DocumentCategoryResponse toResponse(DocumentCategoryEntity entity, long documentCount) {
        return new DocumentCategoryResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getDisplayOrder(),
                entity.getIsActive(),
                documentCount,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category code is required");
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category code must not exceed 80 characters");
        }
        if (!normalized.matches("^[a-z0-9_-]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category code must contain only lowercase letters, digits, underscores, and dashes");
        }
        return normalized;
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category name is required");
        }
        String normalized = name.trim();
        if (normalized.length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category name must not exceed 150 characters");
        }
        return normalized;
    }

    private String normalizeDescription(String desc) {
        if (desc == null) return null;
        String normalized = desc.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > 5000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description must not exceed 5000 characters");
        }
        return normalized;
    }

    private Map<String, Object> snapshot(DocumentCategoryEntity entity) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", entity.getId());
        data.put("code", entity.getCode());
        data.put("name", entity.getName());
        data.put("description", entity.getDescription());
        data.put("displayOrder", entity.getDisplayOrder());
        data.put("isActive", entity.getIsActive());
        return data;
    }
}
