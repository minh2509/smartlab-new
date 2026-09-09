package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateResearchFieldRequest;
import com.smartlab.dto.request.UpdateResearchFieldRequest;
import com.smartlab.entity.ResearchFieldEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.repo.ResearchFieldRepository;
import com.smartlab.repo.StoredFileRepository;
import com.smartlab.service.PostContentFileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchFieldServiceImplTest {
    @Mock ResearchFieldRepository researchFieldRepository;
    @Mock StoredFileRepository storedFileRepository;
    @Mock PostContentFileService postContentFileService;
    @InjectMocks ResearchFieldServiceImpl service;

    @Test
    void returnsActiveFieldByCodeCaseInsensitively() {
        ResearchFieldEntity field = field(7L);
        when(researchFieldRepository.findByCodeIgnoreCaseAndIsActiveTrue("ai")).thenReturn(Optional.of(field));

        assertThat(service.getActiveByCode("ai"))
                .extracting("id", "code", "name")
                .containsExactly(7L, "AI", "Artificial Intelligence");
        verify(researchFieldRepository).findByCodeIgnoreCaseAndIsActiveTrue(eq("ai"));
    }

    @Test
    void rejectsUnknownFieldCode() {
        when(researchFieldRepository.findByCodeIgnoreCaseAndIsActiveTrue("UNKNOWN")).thenReturn(Optional.empty());

        assertNotFound(() -> service.getActiveByCode("UNKNOWN"));
    }

    @Test
    void rejectsInactiveFieldCode() {
        when(researchFieldRepository.findByCodeIgnoreCaseAndIsActiveTrue("INACTIVE")).thenReturn(Optional.empty());

        assertNotFound(() -> service.getActiveByCode("INACTIVE"));
    }

    @Test
    void createsFieldWithoutCoverAndReturnsNullCoverFileId() {
        when(researchFieldRepository.existsByCodeIgnoreCase("AI")).thenReturn(false);
        when(researchFieldRepository.save(any())).thenAnswer(invocation -> {
            ResearchFieldEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        assertThat(service.create(createRequest(null))).extracting("id", "coverFileId").containsExactly(1L, null);
    }

    @Test
    void createsFieldWithValidPublicImageAndReturnsCoverFileId() {
        StoredFileEntity cover = cover(10L);
        activeMetadata(10L, "image/png", true, "PUBLIC");
        when(storedFileRepository.getReferenceById(10L)).thenReturn(cover);
        when(researchFieldRepository.save(any())).thenAnswer(invocation -> {
            ResearchFieldEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        assertThat(service.create(createRequest(10L)).getCoverFileId()).isEqualTo(10L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRIVATE", "LAB", "PROJECT"})
    void rejectsNonPublicImageCovers(String accessScope) {
        activeMetadata(10L, "image/png", true, accessScope);

        assertBadRequest(() -> service.create(createRequest(10L)));
        verify(researchFieldRepository, never()).save(any());
    }

    @Test
    void rejectsNonImageMissingAndDeletedCovers() {
        activeMetadata(10L, "application/pdf", false, "PUBLIC");
        assertBadRequest(() -> service.create(createRequest(10L)));
        when(postContentFileService.findActiveMetadata(11L)).thenReturn(Optional.empty());
        assertBadRequest(() -> service.create(createRequest(11L)));
        when(postContentFileService.findActiveMetadata(12L)).thenReturn(Optional.empty());
        assertBadRequest(() -> service.create(createRequest(12L)));
        verify(researchFieldRepository, never()).save(any());
    }

    @Test
    void preservesCoverWhenUpdateDoesNotIncludeCoverArguments() {
        ResearchFieldEntity field = field(7L);
        field.setCoverFile(cover(10L));
        when(researchFieldRepository.findById(7L)).thenReturn(Optional.of(field));
        when(researchFieldRepository.save(field)).thenReturn(field);
        UpdateResearchFieldRequest request = new UpdateResearchFieldRequest();
        request.setName("Updated AI");

        assertThat(service.update(7L, request).getCoverFileId()).isEqualTo(10L);
    }

    @Test
    void attachesAndReplacesCoverWithPublicImage() {
        ResearchFieldEntity field = field(7L);
        field.setCoverFile(cover(10L));
        StoredFileEntity replacement = cover(11L);
        when(researchFieldRepository.findById(7L)).thenReturn(Optional.of(field));
        when(researchFieldRepository.save(field)).thenReturn(field);
        activeMetadata(11L, "image/webp", true, "PUBLIC");
        when(storedFileRepository.getReferenceById(11L)).thenReturn(replacement);
        UpdateResearchFieldRequest request = new UpdateResearchFieldRequest();
        request.setCoverFileId(11L);

        assertThat(service.update(7L, request).getCoverFileId()).isEqualTo(11L);
    }

    @Test
    void removesCoverOnlyWhenRemoveCoverIsTrue() {
        ResearchFieldEntity field = field(7L);
        field.setCoverFile(cover(10L));
        when(researchFieldRepository.findById(7L)).thenReturn(Optional.of(field));
        when(researchFieldRepository.save(field)).thenReturn(field);
        UpdateResearchFieldRequest request = new UpdateResearchFieldRequest();
        request.setRemoveCover(true);

        assertThat(service.update(7L, request).getCoverFileId()).isNull();
    }

    @Test
    void rejectsConflictingCoverUpdateWithoutReplacingExistingCover() {
        ResearchFieldEntity field = field(7L);
        StoredFileEntity currentCover = cover(10L);
        field.setCoverFile(currentCover);
        when(researchFieldRepository.findById(7L)).thenReturn(Optional.of(field));
        UpdateResearchFieldRequest request = new UpdateResearchFieldRequest();
        request.setCoverFileId(11L);
        request.setRemoveCover(true);

        assertBadRequest(() -> service.update(7L, request));
        assertThat(field.getCoverFile()).isSameAs(currentCover);
        verify(researchFieldRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidReplacementAndPreservesExistingCover() {
        ResearchFieldEntity field = field(7L);
        StoredFileEntity currentCover = cover(10L);
        field.setCoverFile(currentCover);
        when(researchFieldRepository.findById(7L)).thenReturn(Optional.of(field));
        activeMetadata(11L, "application/pdf", false, "PUBLIC");
        UpdateResearchFieldRequest request = new UpdateResearchFieldRequest();
        request.setCoverFileId(11L);

        assertBadRequest(() -> service.update(7L, request));
        assertThat(field.getCoverFile()).isSameAs(currentCover);
        verify(researchFieldRepository, never()).save(any());
    }

    @Test
    void activeListIncludesCoverFileIdAndOldFieldsReturnNull() {
        ResearchFieldEntity covered = field(7L);
        covered.setCoverFile(cover(10L));
        ResearchFieldEntity old = field(8L);
        when(researchFieldRepository.findByIsActiveTrueOrderByNameAsc()).thenReturn(List.of(covered, old));

        assertThat(service.listActive()).extracting("coverFileId").containsExactly(10L, null);
    }

    @Test
    void deactivatesExistingFieldWithoutPhysicallyDeletingIt() {
        ResearchFieldEntity field = field(7L);
        when(researchFieldRepository.findById(7L)).thenReturn(Optional.of(field));

        service.delete(7L);

        assertThat(field.getIsActive()).isFalse();
        verify(researchFieldRepository).save(field);
        verify(researchFieldRepository, never()).delete(field);
    }

    @Test
    void rejectsDeletingMissingField() {
        when(researchFieldRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L)).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private CreateResearchFieldRequest createRequest(Long coverFileId) {
        CreateResearchFieldRequest request = new CreateResearchFieldRequest();
        request.setCode("AI");
        request.setName("Artificial Intelligence");
        request.setCoverFileId(coverFileId);
        return request;
    }

    private void activeMetadata(Long id, String mimeType, boolean image, String accessScope) {
        when(postContentFileService.findActiveMetadata(id)).thenReturn(Optional.of(
                new PostContentFileService.FileMetadata(id, 1L, mimeType, "cover", image, accessScope)));
    }

    private void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private ResearchFieldEntity field(Long id) {
        return ResearchFieldEntity.builder().id(id).code("AI").name("Artificial Intelligence").isActive(true).build();
    }

    private StoredFileEntity cover(Long id) {
        return StoredFileEntity.builder().id(id).mimeType("image/png").accessScope("PUBLIC").build();
    }
}
