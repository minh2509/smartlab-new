package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateResearchPublicationRequest;
import com.smartlab.dto.request.UpdateResearchPublicationRequest;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.dto.response.PublicationYearCountResponse;
import com.smartlab.dto.response.ResearchPublicationResponse;
import com.smartlab.entity.ResearchPublicationEntity;
import com.smartlab.enums.PublicationType;
import com.smartlab.repo.ResearchPublicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchPublicationServiceImplTest {
    @Mock private ResearchPublicationRepository repository;
    @InjectMocks private ResearchPublicationServiceImpl service;

    @Test void defaultsToNewestPublicYearAndPaginatesAtRepository() {
        when(repository.findNewestPublicYear()).thenReturn(2026);
        when(repository.findPublicByYear(eq(2026), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(publication(1L)), org.springframework.data.domain.PageRequest.of(0, 8), 1));
        PublicPageResponse<ResearchPublicationResponse> page = service.listPublic(null, 0, 8);
        assertThat(page.items()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findPublicByYear(eq(2026), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(8);
    }

    @Test void rejectsInvalidPublicPageSize() {
        assertThatThrownBy(() -> service.listPublic(2026, 0, 25)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test void listsPublicYearsInRepositoryOrder() {
        when(repository.findPublicYearCounts()).thenReturn(List.of(
                new Object[]{2026, 12L}, new Object[]{2025, 8L}
        ));

        assertThat(service.listPublicYears()).containsExactly(
                new PublicationYearCountResponse(2026, 12),
                new PublicationYearCountResponse(2025, 8)
        );
    }

    @Test void explicitYearSkipsNewestLookupAndPreservesCompletePaginationMetadata() {
        when(repository.findPublicByYear(eq(2025), any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(publication(1L), publication(2L)), org.springframework.data.domain.PageRequest.of(1, 2), 5)
        );

        PublicPageResponse<ResearchPublicationResponse> page = service.listPublic(2025, 1, 2);

        assertThat(page.items()).extracting(ResearchPublicationResponse::id).containsExactly(1L, 2L);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        verify(repository, never()).findNewestPublicYear();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findPublicByYear(eq(2025), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
    }

    @Test void noPublicYearReturnsEmptyMetadataWithoutYearPageQuery() {
        when(repository.findNewestPublicYear()).thenReturn(null);

        PublicPageResponse<ResearchPublicationResponse> page = service.listPublic(null, 2, 8);

        assertThat(page.items()).isEmpty();
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(8);
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
        verify(repository, never()).findPublicByYear(any(Integer.class), any(Pageable.class));
    }

    @Test void createsValidatedPublicationWithoutHardCodedContent() {
        CreateResearchPublicationRequest request = new CreateResearchPublicationRequest();
        request.setTitle("  Publication title "); request.setAuthors(" A. Author "); request.setVenue(" Venue ");
        request.setPublicationType(PublicationType.JOURNAL_ARTICLE); request.setPublicationYear(2026); request.setIsPublic(true);
        when(repository.saveAndFlush(any())).thenAnswer(call -> { ResearchPublicationEntity value = call.getArgument(0); ReflectionTestUtils.setField(value, "id", 9L); return value; });
        ResearchPublicationResponse response = service.create(request);
        assertThat(response.id()).isEqualTo(9L); assertThat(response.title()).isEqualTo("Publication title");
    }

    @Test void softDeleteHidesPublicationThroughRepositoryContract() {
        ResearchPublicationEntity entity = publication(1L);
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(entity));
        service.delete(1L);
        verify(repository).saveAndFlush(entity);
        assertThat(entity.getDeletedAt()).isNotNull();
    }

    @Test void explicitNullOptionalPatchFieldsClearValues() {
        ResearchPublicationEntity entity = ResearchPublicationEntity.create("Title", "Authors", PublicationType.JOURNAL_ARTICLE, "Venue", 2026, LocalDate.of(2026, 1, 1), "10.1/x", "https://example.test", "Summary", true, Instant.now());
        ReflectionTestUtils.setField(entity, "id", 1L);
        com.smartlab.dto.request.UpdateResearchPublicationRequest request = new com.smartlab.dto.request.UpdateResearchPublicationRequest();
        request.setPublicationDate(null); request.setDoi(null); request.setPublicUrl(null); request.setSummary(null);
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(entity)).thenReturn(entity);
        service.update(1L, request);
        assertThat(entity.getPublicationDate()).isNull(); assertThat(entity.getDoi()).isNull();
        assertThat(entity.getPublicUrl()).isNull(); assertThat(entity.getSummary()).isNull();
    }

    @Test void omittedOptionalPatchFieldsKeepExistingValues() {
        ResearchPublicationEntity entity = detailedPublication(1L);
        UpdateResearchPublicationRequest request = new UpdateResearchPublicationRequest();
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(entity)).thenReturn(entity);

        service.update(1L, request);

        assertThat(entity.getPublicationDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(entity.getDoi()).isEqualTo("10.1/x");
        assertThat(entity.getPublicUrl()).isEqualTo("https://example.test");
        assertThat(entity.getSummary()).isEqualTo("Summary");
    }

    @Test void updateChangesRequestedCoreFields() {
        ResearchPublicationEntity entity = detailedPublication(1L);
        UpdateResearchPublicationRequest request = new UpdateResearchPublicationRequest();
        request.setTitle("Updated title");
        request.setPublicationYear(2025);
        request.setPublicationType(PublicationType.CONFERENCE_PAPER);
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(entity)).thenReturn(entity);

        ResearchPublicationResponse response = service.update(1L, request);

        assertThat(response.title()).isEqualTo("Updated title");
        assertThat(response.publicationYear()).isEqualTo(2025);
        assertThat(response.publicationType()).isEqualTo(PublicationType.CONFERENCE_PAPER);
    }

    private static ResearchPublicationEntity publication(Long id) {
        ResearchPublicationEntity entity = ResearchPublicationEntity.create("Title", "Authors", PublicationType.JOURNAL_ARTICLE, "Venue", 2026, null, null, null, null, true, Instant.now());
        ReflectionTestUtils.setField(entity, "id", id); return entity;
    }

    private static ResearchPublicationEntity detailedPublication(Long id) {
        ResearchPublicationEntity entity = ResearchPublicationEntity.create(
                "Title", "Authors", PublicationType.JOURNAL_ARTICLE, "Venue", 2026,
                LocalDate.of(2026, 1, 1), "10.1/x", "https://example.test", "Summary", true, Instant.now()
        );
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
