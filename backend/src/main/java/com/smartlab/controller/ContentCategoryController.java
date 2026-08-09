package com.smartlab.controller;

import com.smartlab.dto.response.ContentCategoryResponse;
import com.smartlab.service.ContentCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
}
