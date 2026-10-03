package com.smartlab.service.impl;

import com.smartlab.dto.response.PublicDocumentCategoryResponse;
import com.smartlab.dto.response.PublicDocumentSummaryResponse;
import com.smartlab.entity.DocumentCategoryEntity;
import com.smartlab.entity.DocumentEntity;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.enums.FileAccessScope;
import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import com.smartlab.enums.PublicDocumentFileType;
import com.smartlab.enums.PublicDocumentSort;
import com.smartlab.repo.DocumentCategoryRepository;
import com.smartlab.repo.DocumentRepository;
import com.smartlab.repo.DocumentVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicDocumentServiceImplTest {
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentVersionRepository documentVersionRepository;
    @Mock private DocumentCategoryRepository documentCategoryRepository;

    @Test
    void mapsOnlyPublicSummaryFieldsAndUsesDatabasePagingForLatest() {
        DocumentEntity document = document();
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(document)));
        DocumentVersionRepository.VersionNumberProjection version = mock(DocumentVersionRepository.VersionNumberProjection.class);
        when(version.getDocumentId()).thenReturn(31L);
        when(version.getMaxVersionNo()).thenReturn(3);
        when(documentVersionRepository.findMaxVersionNosByDocumentIds(any())).thenReturn(List.of(version));
        PublicDocumentServiceImpl service = new PublicDocumentServiceImpl(documentRepository, documentVersionRepository, documentCategoryRepository);

        var page = service.list("  Robot ", 7L, "RESEARCH", PublicDocumentFileType.PDF, 2026, PublicDocumentSort.LATEST, 1, 12);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(31L);
            assertThat(item.projectCode()).isEqualTo("AI");
            assertThat(item.categoryCode()).isEqualTo("RESEARCH");
            assertThat(item.categoryName()).isEqualTo("Nghiên cứu");
            assertThat(item.currentVersionNo()).isEqualTo(3);
        });
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(documentRepository).findAll(any(Specification.class), pageable.capture());
        verify(documentVersionRepository).findMaxVersionNosByDocumentIds(List.of(31L));
        verify(documentVersionRepository, never()).findMaxVersionNo(31L);
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(12);
        assertThat(pageable.getValue().getSort().getOrderFor("archiveDate").getDirection()).isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
        assertThat(pageable.getValue().getSort().getOrderFor("id").getDirection()).isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
    }

    @Test
    void supportsOldestAndTitleSortAndRejectsInvalidBounds() {
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        PublicDocumentServiceImpl service = new PublicDocumentServiceImpl(documentRepository, documentVersionRepository, documentCategoryRepository);
        service.list(null, null, null, PublicDocumentFileType.ALL, null, PublicDocumentSort.OLDEST, 0, 12);
        service.list(null, null, null, PublicDocumentFileType.ALL, null, PublicDocumentSort.TITLE_ASC, 0, 12);
        service.list(null, null, null, PublicDocumentFileType.ALL, null, PublicDocumentSort.TITLE_DESC, 0, 12);
        assertThatThrownBy(() -> service.list(null, -1L, null, PublicDocumentFileType.ALL, null, PublicDocumentSort.LATEST, 0, 12)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.list(null, null, null, PublicDocumentFileType.ALL, 1900, PublicDocumentSort.LATEST, 0, 12)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.list(null, null, null, PublicDocumentFileType.ALL, null, PublicDocumentSort.LATEST, -1, 12)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void categoriesReturnsActiveCategoriesWithCounts() {
        DocumentCategoryEntity category = DocumentCategoryEntity.create("RESEARCH", "Nghiên cứu", "Tài liệu", 1, true);
        ReflectionTestUtils.setField(category, "id", 1L);
        when(documentCategoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAscNameAscIdAsc()).thenReturn(List.of(category));

        DocumentCategoryRepository.CategoryCountProjection projection = new DocumentCategoryRepository.CategoryCountProjection() {
            @Override public Long getCategoryId() { return 1L; }
            @Override public long getDocumentCount() { return 10L; }
        };
        when(documentCategoryRepository.countPublicDocumentsByCategory(2026)).thenReturn(List.of(projection));

        PublicDocumentServiceImpl service = new PublicDocumentServiceImpl(documentRepository, documentVersionRepository, documentCategoryRepository);
        List<PublicDocumentCategoryResponse> result = service.categories(2026);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("RESEARCH");
        assertThat(result.get(0).documentCount()).isEqualTo(10L);
    }

    @Test
    void inactiveCategoryRemainsPublicButIsOmittedFromPublicMetadata() {
        DocumentEntity document = document();
        document.getCategory().setActive(false);
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(document)));
        PublicDocumentServiceImpl service = new PublicDocumentServiceImpl(documentRepository, documentVersionRepository, documentCategoryRepository);

        var result = service.list(null, null, null, PublicDocumentFileType.ALL, null, PublicDocumentSort.LATEST, 0, 12);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.categoryId()).isNull();
            assertThat(item.categoryCode()).isNull();
            assertThat(item.archiveDate()).isNotNull();
        });
    }

    @Test
    void archiveDateDoesNotChangeWhenDocumentMetadataOrCategoryChanges() {
        DocumentEntity document = document();
        Instant archiveDate = document.getArchiveDate();
        document.updateMetadata("Changed title", "Changed description");
        document.assignCategory(DocumentCategoryEntity.create("OTHER", "Other", null, 2, true));

        assertThat(document.getArchiveDate()).isEqualTo(archiveDate);
    }

    private static DocumentEntity document() {
        ProjectEntity project = ProjectEntity.create("AI", "AI Project", "Public", null, ProjectType.RESEARCH, null,
                ProjectStatus.IN_PROGRESS, LocalDate.of(2026, 1, 1), null, null, true, false, false, null);
        ReflectionTestUtils.setField(project, "id", 7L);
        StoredFileEntity file = StoredFileEntity.builder().id(101L).projectId(7L).storageProvider("TEST")
                .storageKey("internal").originalName("guide.pdf").mimeType("application/pdf").sizeBytes(1024L)
                .accessScope(FileAccessScope.PUBLIC.name()).build();
        DocumentEntity document = DocumentEntity.create(project, "Public guide", "Description", file, null);
        ReflectionTestUtils.setField(document, "id", 31L);
        ReflectionTestUtils.setField(document, "updatedAt", Instant.parse("2026-08-22T00:00:00Z"));

        DocumentCategoryEntity category = DocumentCategoryEntity.create("RESEARCH", "Nghiên cứu", "Tài liệu", 1, true);
        ReflectionTestUtils.setField(category, "id", 1L);
        document.assignCategory(category);

        return document;
    }
}
