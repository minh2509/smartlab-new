package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateLabAchievementRequest;
import com.smartlab.dto.request.UpdateLabAchievementRequest;
import com.smartlab.dto.response.AchievementYearCountResponse;
import com.smartlab.dto.response.AdminLabAchievementResponse;
import com.smartlab.dto.response.LabAchievementResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.entity.LabAchievementEntity;
import com.smartlab.entity.LabAchievementFileEntity;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.enums.AchievementType;
import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import com.smartlab.repo.LabAchievementRepository;
import com.smartlab.repo.LabAchievementFileRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.StoredFileRepository;
import com.smartlab.service.FileService;
import com.smartlab.service.PostContentFileService;
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
import java.time.Year;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabAchievementServiceImplTest {
    @Mock private LabAchievementRepository achievementRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private LabAchievementFileRepository achievementFileRepository;
    @Mock private StoredFileRepository storedFileRepository;
    @Mock private FileService fileService;
    @Mock private PostContentFileService postContentFileService;
    @InjectMocks private LabAchievementServiceImpl service;

    @Test void publicYearsKeepRepositoryNewestFirstCounts() {
        when(achievementRepository.findPublicYearCounts()).thenReturn(List.of(new Object[]{2026, 18L}, new Object[]{2025, 2L}));
        assertThat(service.listPublicYears()).containsExactly(new AchievementYearCountResponse(2026, 18), new AchievementYearCountResponse(2025, 2));
    }

    @Test void omittedYearUsesNewestPublicYearAndRepositoryPagination() {
        when(achievementRepository.findNewestPublicYear()).thenReturn(2026);
        when(achievementRepository.findPublicByYear(eq(2026), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(achievement(1L, null, null)), org.springframework.data.domain.PageRequest.of(1, 2), 5));
        PublicPageResponse<LabAchievementResponse> result = service.listPublic(null, 1, 2);
        assertThat(result.page()).isEqualTo(1); assertThat(result.size()).isEqualTo(2); assertThat(result.totalElements()).isEqualTo(5); assertThat(result.totalPages()).isEqualTo(3);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(achievementRepository).findPublicByYear(eq(2026), page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(1); assertThat(page.getValue().getPageSize()).isEqualTo(2);
    }

    @Test void explicitYearSkipsNewestLookupAndEmptyStateDoesNotQueryPage() {
        when(achievementRepository.findPublicByYear(eq(2025), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        service.listPublic(2025, 0, 8);
        verify(achievementRepository, never()).findNewestPublicYear();
        when(achievementRepository.findNewestPublicYear()).thenReturn(null);
        PublicPageResponse<LabAchievementResponse> empty = service.listPublic(null, 2, 8);
        assertThat(empty.items()).isEmpty(); assertThat(empty.totalElements()).isZero(); assertThat(empty.totalPages()).isZero();
    }

    @Test void rejectsInvalidPublicYearPageAndSizeBeforeQuerying() {
        assertBadRequest(() -> service.listPublic(1899, 0, 8));
        assertBadRequest(() -> service.listPublic(2026, -1, 8));
        assertBadRequest(() -> service.listPublic(2026, 0, 0));
        assertBadRequest(() -> service.listPublic(2026, 0, 25));
        verifyNoInteractions(achievementRepository, projectRepository);
    }

    @Test void adminListUsesDatabaseFiltersPaginationAndIncludesPrivateAchievements() {
        LabAchievementEntity privateAchievement = achievement(2L, project(8L, false, false), LocalDate.of(2026, 8, 20));
        ReflectionTestUtils.setField(privateAchievement, "isPublic", false);
        when(achievementRepository.findActiveForAdmin(eq(2026), eq(AchievementType.AWARD), eq(false), eq("robot"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(privateAchievement), org.springframework.data.domain.PageRequest.of(1, 20), 21));

        PublicPageResponse<AdminLabAchievementResponse> result = service.listAdmin(2026, AchievementType.AWARD, false, " robot ", 1, 20);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.isPublic()).isFalse();
            assertThat(item.relatedProjectId()).isEqualTo(8L);
        });
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(achievementRepository).findActiveForAdmin(eq(2026), eq(AchievementType.AWARD), eq(false), eq("robot"), page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(1); assertThat(page.getValue().getPageSize()).isEqualTo(20);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test void absentOrBlankAdminQueryUsesSafeNoFilterRepresentationBeforeLengthValidation() {
        when(achievementRepository.findActiveForAdmin(eq(null), eq(null), eq(null), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        service.listAdmin(null, null, null, null, 0, 20);
        service.listAdmin(null, null, null, " ".repeat(201), 0, 20);
        verify(achievementRepository, times(2)).findActiveForAdmin(eq(null), eq(null), eq(null), eq(""), any(Pageable.class));
    }

    @Test void invalidAdminParametersDoNotQuery() {
        assertBadRequest(() -> service.listAdmin(null, null, null, null, -1, 20));
        assertBadRequest(() -> service.listAdmin(null, null, null, null, 0, 0));
        assertBadRequest(() -> service.listAdmin(null, null, null, null, 0, 101));
        assertBadRequest(() -> service.listAdmin(1899, null, null, null, 0, 20));
        assertBadRequest(() -> service.listAdmin(null, null, null, "x".repeat(201), 0, 20));
    }

    @Test void createValidatesDateUrlAndActiveRelatedProject() {
        CreateLabAchievementRequest request = createRequest(); request.setAchievementDate(LocalDate.of(2025, 1, 1));
        assertBadRequest(() -> service.create(request));
        request.setAchievementDate(LocalDate.of(2026, 1, 1)); request.setEvidenceUrl("ftp://example.test");
        assertBadRequest(() -> service.create(request));
        request.setEvidenceUrl("https://example.test/evidence"); request.setRelatedProjectId(9L);
        when(projectRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.empty());
        assertBadRequest(() -> service.create(request));
    }

    @Test void createRejectsYearsOutsideAchievementDomainAndAcceptsCurrentYear() {
        CreateLabAchievementRequest request = createRequest();
        request.setAchievementYear(2022);
        assertBadRequest(() -> service.create(request));

        request.setAchievementYear(Year.now().getValue() + 1);
        assertBadRequest(() -> service.create(request));

        request.setAchievementYear(Year.now().getValue());
        when(achievementRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.create(request).achievementYear()).isEqualTo(Year.now().getValue());
    }

    @Test void createAcceptsHttpAndHttpsAndDefaultsPrivate() {
        CreateLabAchievementRequest request = createRequest(); request.setEvidenceUrl("http://example.test/evidence");
        when(achievementRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            LabAchievementEntity value = invocation.getArgument(0); ReflectionTestUtils.setField(value, "id", 9L); return value;
        });
        AdminLabAchievementResponse result = service.create(request);
        assertThat(result.id()).isEqualTo(9L); assertThat(result.isPublic()).isFalse(); assertThat(result.evidenceUrl()).isEqualTo("http://example.test/evidence");
    }

    @Test void publicResponseOnlyExposesMinimalPublicProject() {
        ProjectEntity publicProject = project(7L, true, false);
        LabAchievementEntity visible = achievement(1L, publicProject, null);
        ProjectEntity privateProject = project(8L, false, false);
        LabAchievementEntity privateMasked = achievement(2L, privateProject, null);
        ProjectEntity deletedProject = project(9L, true, true);
        LabAchievementEntity deletedMasked = achievement(3L, deletedProject, null);
        when(achievementRepository.findPublicByYear(eq(2026), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(visible, privateMasked, deletedMasked)));
        List<LabAchievementResponse> items = service.listPublic(2026, 0, 8).items();
        assertThat(items.get(0).relatedProject()).extracting("id", "code", "name").containsExactly(7L, "SL-7", "Project 7");
        assertThat(items.get(1).relatedProject()).isNull();
        assertThat(items.get(2).relatedProject()).isNull();
    }

    @Test void adminCreateAndUpdateExposeStoredPrivateRelatedProjectId() {
        ProjectEntity privateProject = project(8L, false, false);
        CreateLabAchievementRequest create = createRequest(); create.setRelatedProjectId(8L);
        when(projectRepository.findByIdAndDeletedAtIsNull(8L)).thenReturn(Optional.of(privateProject));
        when(achievementRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            LabAchievementEntity value = invocation.getArgument(0); ReflectionTestUtils.setField(value, "id", 9L); return value;
        });
        assertThat(service.create(create).relatedProjectId()).isEqualTo(8L);

        LabAchievementEntity existing = achievement(1L, privateProject, LocalDate.of(2026, 1, 1));
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(existing));
        when(achievementRepository.saveAndFlush(existing)).thenReturn(existing);
        assertThat(service.update(1L, new UpdateLabAchievementRequest()).relatedProjectId()).isEqualTo(8L);
    }

    @Test void patchHonorsExplicitNullAndValidatesFinalDateYearAndProject() {
        ProjectEntity project = project(7L, true, false);
        LabAchievementEntity entity = achievement(1L, project, LocalDate.of(2026, 1, 1));
        UpdateLabAchievementRequest clear = new UpdateLabAchievementRequest();
        clear.setSummary(null); clear.setAchievementDate(null); clear.setEvidenceUrl(null); clear.setRelatedProjectId(null);
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(entity));
        when(achievementRepository.saveAndFlush(entity)).thenReturn(entity);
        service.update(1L, clear);
        assertThat(entity.getSummary()).isNull(); assertThat(entity.getAchievementDate()).isNull(); assertThat(entity.getEvidenceUrl()).isNull(); assertThat(entity.getRelatedProject()).isNull();

        UpdateLabAchievementRequest mismatch = new UpdateLabAchievementRequest(); mismatch.setAchievementDate(LocalDate.of(2025, 1, 1));
        assertBadRequest(() -> service.update(1L, mismatch));
    }

    @Test void patchRejectsYearsOutsideAchievementDomain() {
        LabAchievementEntity entity = achievement(1L, null, LocalDate.of(2026, 1, 1));
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(entity));

        UpdateLabAchievementRequest beforeStart = new UpdateLabAchievementRequest(); beforeStart.setAchievementYear(2022);
        assertBadRequest(() -> service.update(1L, beforeStart));

        UpdateLabAchievementRequest future = new UpdateLabAchievementRequest(); future.setAchievementYear(Year.now().getValue() + 1);
        assertBadRequest(() -> service.update(1L, future));
    }

    @Test void omittedOptionalPatchFieldsPreserveValuesAndSoftDeleteMarksEntity() {
        LabAchievementEntity entity = achievement(1L, null, LocalDate.of(2026, 1, 1));
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(entity));
        when(achievementRepository.saveAndFlush(entity)).thenReturn(entity);
        service.update(1L, new UpdateLabAchievementRequest());
        assertThat(entity.getSummary()).isEqualTo("Summary"); assertThat(entity.getEvidenceUrl()).isEqualTo("https://example.test/evidence");
        service.delete(1L);
        assertThat(entity.getDeletedAt()).isNotNull(); verify(achievementRepository, times(2)).saveAndFlush(entity);
    }

    @Test void recognizingOrganizationIsExposedInAdminAndPublicResponsesAndHonorsPatchPresence() {
        CreateLabAchievementRequest create = createRequest(); create.setRecognizingOrganization("  SmartLab  ");
        when(achievementRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AdminLabAchievementResponse adminResponse = service.create(create);
        assertThat(adminResponse.recognizingOrganization()).isEqualTo("SmartLab");

        LabAchievementEntity publicAchievement = achievement(2L, null, null);
        ReflectionTestUtils.setField(publicAchievement, "recognizingOrganization", "Public organization");
        when(achievementRepository.findPublicByYear(eq(2026), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(publicAchievement)));
        assertThat(service.listPublic(2026, 0, 8).items()).singleElement()
                .extracting(LabAchievementResponse::recognizingOrganization).isEqualTo("Public organization");

        create.setRecognizingOrganization(" ");
        assertThat(service.create(create).recognizingOrganization()).isNull();

        LabAchievementEntity entity = achievement(1L, null, null);
        ReflectionTestUtils.setField(entity, "recognizingOrganization", "Existing org");
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(entity));
        when(achievementRepository.saveAndFlush(entity)).thenReturn(entity);
        service.update(1L, new UpdateLabAchievementRequest());
        assertThat(entity.getRecognizingOrganization()).isEqualTo("Existing org");
        UpdateLabAchievementRequest clear = new UpdateLabAchievementRequest(); clear.setRecognizingOrganization(null);
        service.update(1L, clear);
        assertThat(entity.getRecognizingOrganization()).isNull();
    }

    @Test void uploadFirstFileUsesSortOrderZero() {
        LabAchievementEntity achievement = achievement(1L, null, null);
        StoredFileEntity file = file(11L);
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(achievement));
        when(fileService.upload(any(), eq("PRIVATE"), eq(null), eq("admin@test"))).thenReturn(com.smartlab.dto.response.FileResponse.builder().id(11L).build());
        when(storedFileRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(file));
        when(achievementFileRepository.findMaxActiveSortOrder(1L)).thenReturn(-1);
        when(achievementFileRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            LabAchievementFileEntity value = invocation.getArgument(0); ReflectionTestUtils.setField(value, "id", 5L); return value;
        });
        org.springframework.mock.web.MockMultipartFile upload = new org.springframework.mock.web.MockMultipartFile("file", "evidence.pdf", "application/pdf", new byte[]{1});
        assertThat(service.uploadFile(1L, upload, " ", "admin@test")).extracting("fileId", "label", "sortOrder").containsExactly(11L, null, 0);
    }

    @Test void laterUploadUsesNextActiveSortOrder() {
        LabAchievementEntity achievement = achievement(1L, null, null);
        StoredFileEntity file = file(11L);
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(achievement));
        when(fileService.upload(any(), eq("PRIVATE"), eq(null), eq("admin@test"))).thenReturn(com.smartlab.dto.response.FileResponse.builder().id(11L).build());
        when(storedFileRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(file));
        when(achievementFileRepository.findMaxActiveSortOrder(1L)).thenReturn(0);
        when(achievementFileRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        org.springframework.mock.web.MockMultipartFile upload = new org.springframework.mock.web.MockMultipartFile("file", "evidence.pdf", "application/pdf", new byte[]{1});
        assertThat(service.uploadFile(1L, upload, null, "admin@test").sortOrder()).isEqualTo(1);
    }

    @Test void uploadRequiresActiveAchievementBeforeFileUpload() {
        org.springframework.mock.web.MockMultipartFile upload = new org.springframework.mock.web.MockMultipartFile("file", "evidence.pdf", "application/pdf", new byte[]{1});
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());
        assertNotFound(() -> service.uploadFile(1L, upload, null, "admin@test"));
        when(achievementRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.empty());
        assertNotFound(() -> service.uploadFile(2L, upload, null, "admin@test"));
        verify(fileService, never()).upload(any(), any(), any(), any());
    }

    @Test void detachCorrectMappingSoftDetachesWithoutDeletingStoredFile() {
        LabAchievementEntity achievement = achievement(1L, null, null);
        LabAchievementFileEntity attachment = attachment(4L, achievement, file(11L), "first", 0);
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(achievement));
        when(achievementFileRepository.findActiveByIdAndAchievementId(4L, 1L)).thenReturn(Optional.of(attachment));
        service.detachFile(1L, 4L);
        assertThat(attachment.getDeletedAt()).isNotNull();
        verify(achievementFileRepository).saveAndFlush(attachment);
        verify(fileService, never()).delete(any(), any(), any());
        verifyNoInteractions(postContentFileService);
    }

    @Test void detachWrongAchievementReturnsNotFound() {
        LabAchievementEntity achievement = achievement(1L, null, null);
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(achievement));
        when(achievementFileRepository.findActiveByIdAndAchievementId(4L, 1L)).thenReturn(Optional.empty());
        assertNotFound(() -> service.detachFile(1L, 4L));
    }

    @Test void detachMissingOrAlreadySoftDetachedMappingReturnsNotFound() {
        LabAchievementEntity achievement = achievement(1L, null, null);
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(achievement));
        when(achievementFileRepository.findActiveByIdAndAchievementId(4L, 1L)).thenReturn(Optional.empty());
        assertNotFound(() -> service.detachFile(1L, 4L));
        when(achievementFileRepository.findActiveByIdAndAchievementId(5L, 1L)).thenReturn(Optional.empty());
        assertNotFound(() -> service.detachFile(1L, 5L));
    }

    @Test void publicDownloadSucceedsForPublicActiveAchievementAttachmentAndFile() {
        LabAchievementEntity achievement = achievement(1L, null, null);
        StoredFileEntity file = file(11L, "PUBLIC");
        LabAchievementFileEntity attachment = attachment(6L, achievement, file, null, 1);
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(achievement));
        when(achievementFileRepository.findPublicActiveByIdAndAchievementId(6L, 1L)).thenReturn(Optional.of(attachment));
        when(postContentFileService.findActiveMetadata(11L)).thenReturn(Optional.of(new PostContentFileService.FileMetadata(11L, 1L, "application/pdf", "evidence.pdf", false, "PUBLIC")));
        when(postContentFileService.downloadActiveContent(11L)).thenReturn(new PostContentFileService.DownloadedContent(new byte[]{9}, "application/pdf", "evidence.pdf"));
        assertThat(service.downloadPublicFile(1L, 6L).content()).containsExactly(9);
    }

    @Test void publicDownloadPrivateAttachmentReturnsGenericNotFoundWithoutContentDownload() {
        LabAchievementEntity achievement = achievement(1L, null, null);
        LabAchievementFileEntity attachment = attachment(6L, achievement, file(11L), null, 1);
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(achievement));
        when(achievementFileRepository.findPublicActiveByIdAndAchievementId(6L, 1L)).thenReturn(Optional.empty());
        assertAttachmentNotFound(() -> service.downloadPublicFile(1L, 6L));
        verifyNoInteractions(postContentFileService);
    }

    @Test void publicDownloadRejectsPrivateMetadataEvenIfRepositoryReturnsAttachment() {
        LabAchievementEntity achievement = achievement(1L, null, null);
        LabAchievementFileEntity attachment = attachment(6L, achievement, file(11L, "PUBLIC"), null, 1);
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(achievement));
        when(achievementFileRepository.findPublicActiveByIdAndAchievementId(6L, 1L)).thenReturn(Optional.of(attachment));
        when(postContentFileService.findActiveMetadata(11L)).thenReturn(Optional.of(new PostContentFileService.FileMetadata(11L, 1L, "application/pdf", "evidence.pdf", false, "PRIVATE")));
        assertAttachmentNotFound(() -> service.downloadPublicFile(1L, 6L));
        verify(postContentFileService, never()).downloadActiveContent(any());
    }

    @Test void publicDownloadPrivateAchievementReturnsGenericNotFound() {
        LabAchievementEntity achievement = achievement(1L, null, null);
        ReflectionTestUtils.setField(achievement, "isPublic", false);
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(achievement));
        assertAttachmentNotFound(() -> service.downloadPublicFile(1L, 5L));
    }

    @Test void publicDownloadMissingOrDeletedAchievementReturnsSameGenericNotFound() {
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());
        assertAttachmentNotFound(() -> service.downloadPublicFile(1L, 5L));
        when(achievementRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.empty());
        assertAttachmentNotFound(() -> service.downloadPublicFile(2L, 5L));
    }

    @Test void publicDownloadWrongOrDetachedAttachmentReturnsGenericNotFound() {
        LabAchievementEntity achievement = achievement(1L, null, null);
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(achievement));
        when(achievementFileRepository.findPublicActiveByIdAndAchievementId(5L, 1L)).thenReturn(Optional.empty());
        assertAttachmentNotFound(() -> service.downloadPublicFile(1L, 5L));
        when(achievementFileRepository.findPublicActiveByIdAndAchievementId(6L, 1L)).thenReturn(Optional.empty());
        assertAttachmentNotFound(() -> service.downloadPublicFile(1L, 6L));
    }

    @Test void publicDownloadDeletedAttachmentReturnsGenericNotFound() {
        LabAchievementEntity achievement = achievement(1L, null, null);
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(achievement));
        when(achievementFileRepository.findPublicActiveByIdAndAchievementId(6L, 1L)).thenReturn(Optional.empty());
        assertAttachmentNotFound(() -> service.downloadPublicFile(1L, 6L));
    }

    @Test void publicDownloadMissingOrDeletedFileMetadataReturnsGenericNotFoundWithoutContentDownload() {
        LabAchievementEntity achievement = achievement(1L, null, null);
        LabAchievementFileEntity attachment = attachment(6L, achievement, file(11L), null, 1);
        when(achievementRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(achievement));
        when(achievementFileRepository.findPublicActiveByIdAndAchievementId(6L, 1L)).thenReturn(Optional.of(attachment));
        when(postContentFileService.findActiveMetadata(11L)).thenReturn(Optional.empty());
        assertAttachmentNotFound(() -> service.downloadPublicFile(1L, 6L));
        assertAttachmentNotFound(() -> service.downloadPublicFile(1L, 6L));
        verify(postContentFileService, never()).downloadActiveContent(any());
    }

    private static CreateLabAchievementRequest createRequest() {
        CreateLabAchievementRequest request = new CreateLabAchievementRequest();
        request.setTitle(" Achievement "); request.setAchievementType(AchievementType.AWARD); request.setAchievementYear(2026); return request;
    }

    private static LabAchievementEntity achievement(Long id, ProjectEntity project, LocalDate date) {
        LabAchievementEntity entity = LabAchievementEntity.create("Achievement", "Summary", AchievementType.AWARD, 2026, date,
                "https://example.test/evidence", project, true, Instant.parse("2026-01-02T00:00:00Z"));
        ReflectionTestUtils.setField(entity, "id", id); return entity;
    }

    private static ProjectEntity project(Long id, boolean isPublic, boolean softDeleted) {
        ProjectEntity project = ProjectEntity.create("SL-" + id, "Project " + id, null, null, ProjectType.RESEARCH, null,
                ProjectStatus.IN_PROGRESS, null, null, null, isPublic, false, false, null);
        ReflectionTestUtils.setField(project, "id", id);
        if (softDeleted) ReflectionTestUtils.setField(project, "deletedAt", java.sql.Timestamp.from(Instant.now()));
        return project;
    }

    private static StoredFileEntity file(Long id) {
        return file(id, "PRIVATE");
    }

    private static StoredFileEntity file(Long id, String accessScope) {
        return StoredFileEntity.builder().id(id).originalName("evidence.pdf").mimeType("application/pdf").sizeBytes(12L)
                .accessScope(accessScope).storageKey("key").storageProvider("GOOGLE_DRIVE").build();
    }

    private static LabAchievementFileEntity attachment(Long id, LabAchievementEntity achievement, StoredFileEntity file, String label, int sortOrder) {
        LabAchievementFileEntity entity = LabAchievementFileEntity.create(achievement, file, label, sortOrder, Instant.now());
        ReflectionTestUtils.setField(entity, "id", id); return entity;
    }

    private static void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static void assertAttachmentNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(exception.getReason()).isEqualTo("Achievement attachment not found");
        });
    }
}
