package com.smartlab.service;

import com.smartlab.dto.request.AssignDocumentCategoryRequest;
import com.smartlab.dto.request.CreateDocumentCategoryRequest;
import com.smartlab.dto.request.ReorderDocumentCategoryRequest;
import com.smartlab.dto.request.UpdateDocumentCategoryRequest;
import com.smartlab.dto.response.AdminDocumentItemResponse;
import com.smartlab.dto.response.DocumentCategoryResponse;
import com.smartlab.dto.response.PublicPageResponse;

import java.util.List;

public interface DocumentCategoryService {
    List<DocumentCategoryResponse> listCategories();

    DocumentCategoryResponse createCategory(CreateDocumentCategoryRequest request);

    DocumentCategoryResponse updateCategory(Long id, UpdateDocumentCategoryRequest request);

    DocumentCategoryResponse toggleActive(Long id);

    void reorderCategories(ReorderDocumentCategoryRequest request);

    void assignDocuments(AssignDocumentCategoryRequest request);

    List<AdminDocumentItemResponse> listDocumentsForAssignment();

    PublicPageResponse<AdminDocumentItemResponse> listDocumentsForAssignment(
            String query, Long categoryId, String status, int page, int size
    );
}
