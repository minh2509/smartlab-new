package com.smartlab.service.impl;

import com.smartlab.dto.request.AssignDocumentCategoryRequest;
import com.smartlab.dto.request.CreateDocumentCategoryRequest;
import com.smartlab.dto.request.ReorderDocumentCategoryRequest;
import com.smartlab.dto.request.UpdateDocumentCategoryRequest;
import com.smartlab.dto.response.DocumentCategoryResponse;
import com.smartlab.entity.DocumentCategoryEntity;
import com.smartlab.entity.DocumentEntity;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.enums.FileAccessScope;
import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import com.smartlab.repo.DocumentCategoryRepository;
import com.smartlab.repo.DocumentRepository;
import com.smartlab.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentCategoryServiceImplTest {
    @Mock private DocumentCategoryRepository documentCategoryRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private DocumentCategoryServiceImpl service;

    @Test
    void createCategorySuccess() {
        CreateDocumentCategoryRequest req = new CreateDocumentCategoryRequest("research", "Research Papers", "Description", 1, true);
        when(documentCategoryRepository.existsByCode("research")).thenReturn(false);
        DocumentCategoryEntity saved = DocumentCategoryEntity.create("research", "Research Papers", "Description", 1, true);
        ReflectionTestUtils.setField(saved, "id", 10L);
        when(documentCategoryRepository.saveAndFlush(any(DocumentCategoryEntity.class))).thenReturn(saved);

        DocumentCategoryResponse res = service.createCategory(req);

        assertThat(res.id()).isEqualTo(10L);
        assertThat(res.code()).isEqualTo("research");
        assertThat(res.name()).isEqualTo("Research Papers");
        verify(auditService).log(any(), any(), any(), any(), any());
    }

    @Test
    void createCategoryConflictThrowsException() {
        CreateDocumentCategoryRequest req = new CreateDocumentCategoryRequest("research", "Research Papers", "Description", 1, true);
        when(documentCategoryRepository.existsByCode("research")).thenReturn(true);

        assertThatThrownBy(() -> service.createCategory(req))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updateCategorySuccess() {
        DocumentCategoryEntity existing = DocumentCategoryEntity.create("research", "Old Name", "Old Desc", 1, true);
        ReflectionTestUtils.setField(existing, "id", 10L);
        when(documentCategoryRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(documentCategoryRepository.saveAndFlush(any(DocumentCategoryEntity.class))).thenReturn(existing);

        UpdateDocumentCategoryRequest req = new UpdateDocumentCategoryRequest("New Name", "New Desc", 2, true);
        DocumentCategoryResponse res = service.updateCategory(10L, req);

        assertThat(res.name()).isEqualTo("New Name");
        assertThat(existing.getDisplayOrder()).isEqualTo(2);
    }

    @Test
    void toggleActiveInvertsStatus() {
        DocumentCategoryEntity existing = DocumentCategoryEntity.create("research", "Name", "Desc", 1, true);
        ReflectionTestUtils.setField(existing, "id", 10L);
        when(documentCategoryRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(documentCategoryRepository.saveAndFlush(any(DocumentCategoryEntity.class))).thenReturn(existing);

        DocumentCategoryResponse res = service.toggleActive(10L);

        assertThat(res.isActive()).isFalse();
    }

    @Test
    void assignDocumentsAssignsOrUnassigns() {
        DocumentCategoryEntity cat = DocumentCategoryEntity.create("research", "Name", "Desc", 1, true);
        ReflectionTestUtils.setField(cat, "id", 5L);
        when(documentCategoryRepository.findById(5L)).thenReturn(Optional.of(cat));

        DocumentEntity doc = createSampleDocument(100L);
        when(documentRepository.findAllActiveByIdInForUpdate(any())).thenReturn(List.of(doc));

        service.assignDocuments(new AssignDocumentCategoryRequest(List.of(100L), 5L));

        assertThat(doc.getCategory()).isEqualTo(cat);
        verify(documentRepository).saveAllAndFlush(List.of(doc));
    }

    @Test
    void reorderCategoriesUpdatesOrders() {
        DocumentCategoryEntity cat = DocumentCategoryEntity.create("research", "Name", "Desc", 1, true);
        ReflectionTestUtils.setField(cat, "id", 5L);
        when(documentCategoryRepository.findAllForUpdate()).thenReturn(List.of(cat));

        service.reorderCategories(new ReorderDocumentCategoryRequest(List.of(
                new ReorderDocumentCategoryRequest.OrderItem(5L, 10)
        )));

        assertThat(cat.getDisplayOrder()).isEqualTo(10);
        verify(documentCategoryRepository).saveAllAndFlush(List.of(cat));
    }

    @Test
    void assignmentRejectsDuplicateIdsBeforeAnyLookup() {
        assertThatThrownBy(() -> service.assignDocuments(new AssignDocumentCategoryRequest(List.of(100L, 100L), 5L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("unique");

        verifyNoInteractions(documentCategoryRepository, documentRepository);
    }

    @Test
    void assignmentValidatesEveryDocumentBeforeMutation() {
        DocumentCategoryEntity cat = DocumentCategoryEntity.create("research", "Name", "Desc", 1, true);
        ReflectionTestUtils.setField(cat, "id", 5L);
        DocumentEntity first = createSampleDocument(100L);
        when(documentCategoryRepository.findById(5L)).thenReturn(Optional.of(cat));
        when(documentRepository.findAllActiveByIdInForUpdate(any())).thenReturn(List.of(first));

        assertThatThrownBy(() -> service.assignDocuments(new AssignDocumentCategoryRequest(List.of(100L, 200L), 5L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("200");

        assertThat(first.getCategory()).isNull();
        verify(documentRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void inactiveCategoryCannotBeAssigned() {
        DocumentCategoryEntity cat = DocumentCategoryEntity.create("research", "Name", "Desc", 1, false);
        ReflectionTestUtils.setField(cat, "id", 5L);
        when(documentCategoryRepository.findById(5L)).thenReturn(Optional.of(cat));

        assertThatThrownBy(() -> service.assignDocuments(new AssignDocumentCategoryRequest(List.of(100L), 5L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("inactive");
        verifyNoInteractions(documentRepository);
    }

    @Test
    void assignmentListSupportsBoundedServerPagination() {
        DocumentEntity document = createSampleDocument(100L);
        when(documentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(document)));

        var result = service.listDocumentsForAssignment("guide", null, "UNASSIGNED", 1, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.page()).isEqualTo(0);
        verify(documentRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class));
    }

    @Test
    void reorderRejectsDuplicateIdsAndOrders() {
        assertThatThrownBy(() -> service.reorderCategories(new ReorderDocumentCategoryRequest(List.of(
                new ReorderDocumentCategoryRequest.OrderItem(5L, 1),
                new ReorderDocumentCategoryRequest.OrderItem(5L, 2)
        )))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("unique");

        assertThatThrownBy(() -> service.reorderCategories(new ReorderDocumentCategoryRequest(List.of(
                new ReorderDocumentCategoryRequest.OrderItem(5L, 1),
                new ReorderDocumentCategoryRequest.OrderItem(6L, 1)
        )))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("unique");
        verifyNoInteractions(documentCategoryRepository);
    }

    private DocumentEntity createSampleDocument(Long id) {
        ProjectEntity project = ProjectEntity.create("AI", "AI Project", "Public", null, ProjectType.RESEARCH, null,
                ProjectStatus.IN_PROGRESS, LocalDate.of(2026, 1, 1), null, null, true, false, false, null);
        ReflectionTestUtils.setField(project, "id", 7L);
        StoredFileEntity file = StoredFileEntity.builder().id(101L).projectId(7L).storageProvider("TEST")
                .storageKey("internal").originalName("guide.pdf").mimeType("application/pdf").sizeBytes(1024L)
                .accessScope(FileAccessScope.PUBLIC.name()).build();
        DocumentEntity document = DocumentEntity.create(project, "Public guide", "Description", file, null);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
