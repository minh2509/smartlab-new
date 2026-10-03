package com.smartlab.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.dto.request.AssignDocumentCategoryRequest;
import com.smartlab.dto.request.CreateDocumentCategoryRequest;
import com.smartlab.dto.request.ReorderDocumentCategoryRequest;
import com.smartlab.dto.request.UpdateDocumentCategoryRequest;
import com.smartlab.dto.response.AdminDocumentItemResponse;
import com.smartlab.dto.response.DocumentCategoryResponse;
import com.smartlab.service.DocumentCategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminDocumentCategoryControllerTest {
    private final DocumentCategoryService service = mock(DocumentCategoryService.class);
    private final AdminDocumentCategoryController controller = new AdminDocumentCategoryController(service);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void listCategoriesReturnsAllCategories() throws Exception {
        when(service.listCategories()).thenReturn(List.of(
                new DocumentCategoryResponse(1L, "research", "Nghiên cứu", "Mô tả", 1, true, 5L, Instant.now(), Instant.now())
        ));

        mockMvc.perform(get("/admin/document-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].code").value("research"))
                .andExpect(jsonPath("$[0].name").value("Nghiên cứu"));

        verify(service).listCategories();
    }

    @Test
    void createCategoryInvokesService() throws Exception {
        CreateDocumentCategoryRequest request = new CreateDocumentCategoryRequest("research", "Nghiên cứu", "Mô tả", 1, true);
        when(service.createCategory(any())).thenReturn(
                new DocumentCategoryResponse(1L, "research", "Nghiên cứu", "Mô tả", 1, true, 0L, Instant.now(), Instant.now())
        );

        mockMvc.perform(post("/admin/document-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("research"));

        verify(service).createCategory(any());
    }

    @Test
    void updateCategoryInvokesService() throws Exception {
        UpdateDocumentCategoryRequest request = new UpdateDocumentCategoryRequest("Nghiên cứu mới", "Mô tả", 2, true);
        when(service.updateCategory(eq(1L), any())).thenReturn(
                new DocumentCategoryResponse(1L, "research", "Nghiên cứu mới", "Mô tả", 2, true, 3L, Instant.now(), Instant.now())
        );

        mockMvc.perform(put("/admin/document-categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nghiên cứu mới"));

        verify(service).updateCategory(eq(1L), any());
    }

    @Test
    void toggleActiveInvokesService() throws Exception {
        when(service.toggleActive(1L)).thenReturn(
                new DocumentCategoryResponse(1L, "research", "Nghiên cứu", "Mô tả", 1, false, 3L, Instant.now(), Instant.now())
        );

        mockMvc.perform(patch("/admin/document-categories/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        verify(service).toggleActive(1L);
    }

    @Test
    void reorderCategoriesInvokesService() throws Exception {
        ReorderDocumentCategoryRequest request = new ReorderDocumentCategoryRequest(List.of(
                new ReorderDocumentCategoryRequest.OrderItem(1L, 2),
                new ReorderDocumentCategoryRequest.OrderItem(2L, 1)
        ));

        mockMvc.perform(put("/admin/document-categories/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(service).reorderCategories(any());
    }

    @Test
    void assignDocumentsInvokesService() throws Exception {
        AssignDocumentCategoryRequest request = new AssignDocumentCategoryRequest(List.of(10L, 20L), 1L);

        mockMvc.perform(post("/admin/document-categories/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(service).assignDocuments(any());
    }

    @Test
    void listDocumentsForAssignmentReturnsDocuments() throws Exception {
        when(service.listDocumentsForAssignment()).thenReturn(List.of(
                new AdminDocumentItemResponse(10L, "Doc A", 1L, "PROJ1", "Project 1", 1L, "Nghiên cứu", "doc.pdf", "PUBLIC", Instant.now())
        ));

        mockMvc.perform(get("/admin/document-categories/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].title").value("Doc A"));

        verify(service).listDocumentsForAssignment();
    }

    @Test
    void listDocumentsForAssignmentSupportsServerPagingFilters() throws Exception {
        when(service.listDocumentsForAssignment("guide", 2L, "UNASSIGNED", 1, 20))
                .thenReturn(new com.smartlab.dto.response.PublicPageResponse<>(List.of(), 1, 20, 0, 0));

        mockMvc.perform(get("/admin/document-categories/documents")
                        .param("q", "guide")
                        .param("categoryId", "2")
                        .param("status", "UNASSIGNED")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1));

        verify(service).listDocumentsForAssignment("guide", 2L, "UNASSIGNED", 1, 20);
    }
}
