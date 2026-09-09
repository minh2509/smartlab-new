package com.smartlab.service.impl;

import com.smartlab.dto.response.PublicDocumentSummaryResponse;
import com.smartlab.entity.DocumentEntity;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.enums.FileAccessScope;
import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import com.smartlab.enums.PublicDocumentFileType;
import com.smartlab.enums.PublicDocumentSort;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PublicDocumentServiceImplTest {
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentVersionRepository documentVersionRepository;

    @Test
    void mapsOnlyPublicSummaryFieldsAndUsesDatabasePagingForLatest() {
        DocumentEntity document = document();
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(document)));
        when(documentVersionRepository.findMaxVersionNo(31L)).thenReturn(3);
        PublicDocumentServiceImpl service = new PublicDocumentServiceImpl(documentRepository, documentVersionRepository);

        var page = service.list("  Robot ", 7L, PublicDocumentFileType.PDF, 2026, PublicDocumentSort.LATEST, 1, 12);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(31L);
            assertThat(item.projectCode()).isEqualTo("AI");
            assertThat(item.currentVersionNo()).isEqualTo(3);
        });
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(documentRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(12);
        assertThat(pageable.getValue().getSort().getOrderFor("updatedAt").getDirection()).isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
        assertThat(pageable.getValue().getSort().getOrderFor("id").getDirection()).isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
    }

    @Test
    void supportsOldestAndTitleSortAndRejectsInvalidBounds() {
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        PublicDocumentServiceImpl service = new PublicDocumentServiceImpl(documentRepository, documentVersionRepository);
        service.list(null, null, PublicDocumentFileType.ALL, null, PublicDocumentSort.OLDEST, 0, 12);
        service.list(null, null, PublicDocumentFileType.ALL, null, PublicDocumentSort.TITLE_ASC, 0, 12);
        service.list(null, null, PublicDocumentFileType.ALL, null, PublicDocumentSort.TITLE_DESC, 0, 12);
        assertThatThrownBy(() -> service.list(null, -1L, PublicDocumentFileType.ALL, null, PublicDocumentSort.LATEST, 0, 12)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.list(null, null, PublicDocumentFileType.ALL, 1900, PublicDocumentSort.LATEST, 0, 12)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.list(null, null, PublicDocumentFileType.ALL, null, PublicDocumentSort.LATEST, -1, 12)).isInstanceOf(ResponseStatusException.class);
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
        return document;
    }
}
