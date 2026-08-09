package com.smartlab.service;

import com.smartlab.dto.response.ContentCategoryResponse;

import java.util.List;

public interface ContentCategoryService {
    List<ContentCategoryResponse> getActiveCategories();
}
