package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateContentCategoryRequest;
import com.smartlab.dto.response.ContentCategoryResponse;
import com.smartlab.entity.ContentCategoryEntity;
import com.smartlab.repo.ContentCategoryRepository;
import com.smartlab.service.ContentCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ContentCategoryServiceImpl implements ContentCategoryService {
    private final ContentCategoryRepository contentCategoryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ContentCategoryResponse> getActiveCategories() {
        return contentCategoryRepository.findByIsActiveTrueOrderByIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentCategoryResponse> getAllCategories() {
        return contentCategoryRepository.findAllByOrderByIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ContentCategoryResponse createCategory(CreateContentCategoryRequest request) {
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        String name = request.getName().trim();
        String description = normalizeOptional(request.getDescription());

        if (contentCategoryRepository.existsByCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category code already exists");
        }

        ContentCategoryEntity category = ContentCategoryEntity.create(
                code,
                name,
                description
        );
        try {
            return toResponse(contentCategoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException exception) {
            // The database unique constraint is the final guard for concurrent creates.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category code already exists", exception);
        }
    }

    @Override
    @Transactional
    public ContentCategoryResponse setCategoryActive(Long id, boolean active) {
        ContentCategoryEntity category = contentCategoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        category.setActive(active);
        return toResponse(contentCategoryRepository.saveAndFlush(category));
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        ContentCategoryEntity category = contentCategoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        contentCategoryRepository.delete(category);
        contentCategoryRepository.flush();
    }

    private String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ContentCategoryResponse toResponse(ContentCategoryEntity category) {
        return ContentCategoryResponse.builder()
                .id(category.getId())
                .code(category.getCode())
                .name(category.getName())
                .description(category.getDescription())
                .isActive(category.getIsActive())
                .build();
    }
}
