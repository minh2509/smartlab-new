package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateContentCategoryRequest;
import com.smartlab.dto.response.ContentCategoryResponse;
import com.smartlab.entity.ContentCategoryEntity;
import com.smartlab.repo.ContentCategoryRepository;
import com.smartlab.service.ContentCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

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
    @Transactional
    public ContentCategoryResponse createCategory(CreateContentCategoryRequest request) {
        if (contentCategoryRepository.existsByCode(request.getCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category code already exists");
        }

        ContentCategoryEntity category = ContentCategoryEntity.create(
                request.getCode(),
                request.getName(),
                request.getDescription()
        );
        return toResponse(contentCategoryRepository.save(category));
    }

    private ContentCategoryResponse toResponse(ContentCategoryEntity category) {
        return ContentCategoryResponse.builder()
                .id(category.getId())
                .code(category.getCode())
                .name(category.getName())
                .description(category.getDescription())
                .build();
    }
}
