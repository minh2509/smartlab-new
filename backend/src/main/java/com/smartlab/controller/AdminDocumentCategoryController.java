package com.smartlab.controller;

import com.smartlab.dto.request.AssignDocumentCategoryRequest;
import com.smartlab.dto.request.CreateDocumentCategoryRequest;
import com.smartlab.dto.request.ReorderDocumentCategoryRequest;
import com.smartlab.dto.request.UpdateDocumentCategoryRequest;
import com.smartlab.dto.response.DocumentCategoryResponse;
import com.smartlab.service.DocumentCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/document-categories")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('DOCUMENT_MANAGE')")
public class AdminDocumentCategoryController {
    private final DocumentCategoryService documentCategoryService;

    @GetMapping
    public List<DocumentCategoryResponse> listCategories() {
        return documentCategoryService.listCategories();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentCategoryResponse createCategory(@Valid @RequestBody CreateDocumentCategoryRequest request) {
        return documentCategoryService.createCategory(request);
    }

    @PutMapping("/{id}")
    public DocumentCategoryResponse updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDocumentCategoryRequest request
    ) {
        return documentCategoryService.updateCategory(id, request);
    }

    @PatchMapping("/{id}/status")
    public DocumentCategoryResponse toggleActive(@PathVariable Long id) {
        return documentCategoryService.toggleActive(id);
    }

    @PutMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorderCategories(@Valid @RequestBody ReorderDocumentCategoryRequest request) {
        documentCategoryService.reorderCategories(request);
    }

    @PostMapping("/assign")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignDocuments(@Valid @RequestBody AssignDocumentCategoryRequest request) {
        documentCategoryService.assignDocuments(request);
    }

    @GetMapping("/documents")
    public Object listDocumentsForAssignment(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        if (q == null && categoryId == null && status == null && page == null && size == null) {
            return documentCategoryService.listDocumentsForAssignment();
        }
        return documentCategoryService.listDocumentsForAssignment(
                q, categoryId, status, page == null ? 0 : page, size == null ? 20 : size
        );
    }
}
