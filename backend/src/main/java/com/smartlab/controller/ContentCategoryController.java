package com.smartlab.controller;

import com.smartlab.dto.request.CreateContentCategoryRequest;
import com.smartlab.dto.response.ContentCategoryResponse;
import com.smartlab.service.ContentCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ContentCategoryController {
    private final ContentCategoryService contentCategoryService;

    @GetMapping("/content-categories")
    public List<ContentCategoryResponse> getActiveCategories() {
        return contentCategoryService.getActiveCategories();
    }

    @PostMapping("/admin/content-categories")
    @PreAuthorize("hasAuthority('POST_MANAGE')")
    public ContentCategoryResponse createCategory(@Valid @RequestBody CreateContentCategoryRequest request) {
        return contentCategoryService.createCategory(request);
    }
}
