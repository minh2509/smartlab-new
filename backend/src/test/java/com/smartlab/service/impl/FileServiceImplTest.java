package com.smartlab.service.impl;

import com.smartlab.entity.StoredFileEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import com.smartlab.repo.DocumentRepository;
import com.smartlab.repo.DocumentVersionRepository;
import com.smartlab.repo.GalleryItemRepository;
import com.smartlab.repo.LabAchievementFileRepository;
import com.smartlab.repo.MemberProfileRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.ResearchFieldRepository;
import com.smartlab.repo.StoredFileRepository;
import com.smartlab.repo.TaskAttachmentRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.ProjectAccessService;
import com.smartlab.storage.FileStorage;
import com.smartlab.storage.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {
    @Mock StoredFileRepository storedFileRepository;
    @Mock UserRepository userRepository;
    @Mock FileStorage fileStorage;
    @Mock MemberProfileRepository memberProfileRepository;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectAccessService projectAccessService;
    @Mock DocumentRepository documentRepository;
    @Mock DocumentVersionRepository documentVersionRepository;
    @Mock TaskAttachmentRepository taskAttachmentRepository;
    @Mock ResearchFieldRepository researchFieldRepository;
    @Mock LabAchievementFileRepository achievementFileRepository;
    @Mock GalleryItemRepository galleryItemRepository;
    @InjectMocks FileServiceImpl service;

    private final UserEntity owner = UserEntity.builder().id(1L).email("owner@lab.test").name("Owner").build();

    @BeforeEach
    void configure() {
        ReflectionTestUtils.setField(service, "maxFileSizeBytes", 25L * 1024 * 1024);
        ReflectionTestUtils.setField(service, "storageProvider", "google-drive");
    }

    @Test
    void rejectsProjectScopeUntilMembershipAuthorizationExists() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", pngBytes());

        assertThatThrownBy(() -> service.upload(file, "PROJECT", null, owner.getEmail()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(fileStorage, never()).upload(any(), any(), any(), any());
    }

    @Test
    void rejectsSpoofedContentType() {
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", "not a png".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.upload(file, "PUBLIC", null, owner.getEmail()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        verify(fileStorage, never()).upload(any(), any(), any(), any());
    }

    @Test
    void uploadsAValidRasterImage() {
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(fileStorage.upload(any(), any(), any(), any())).thenReturn(new FileStorage.StoredFile("drive-id", null));
        when(storedFileRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            StoredFileEntity entity = invocation.getArgument(0);
            entity.setId(9L);
            return entity;
        });
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", pngBytes());

        assertThat(service.upload(file, "PUBLIC", "avatar", owner.getEmail()))
                .extracting("id", "mimeType", "accessScope")
                .containsExactly(9L, "image/png", "PUBLIC");
    }

    @Test
    void deniesProjectFileToUnrelatedAuthenticatedMember() {
        StoredFileEntity entity = storedFile(7L, "PROJECT");
        ProjectEntity project = mock(ProjectEntity.class);
        when(storedFileRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(entity));
        when(projectRepository.findByIdAndDeletedAtIsNull(4L)).thenReturn(Optional.of(project));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"))
                .when(projectAccessService).requireRead(project, "other@lab.test");

        assertThat(service.canRead(7L, authentication("other@lab.test"))).isFalse();
        assertThatThrownBy(() -> service.download(7L, authentication("other@lab.test")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(fileStorage, never()).download(any());
    }

    @Test
    void listsAllActiveFilesOwnedByCurrentMemberInRepositoryOrder() {
        StoredFileEntity newest = storedFile(9L, "PRIVATE");
        StoredFileEntity older = storedFile(8L, "LAB");
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(storedFileRepository
                .findAllByOwnerUser_IdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(owner.getId()))
                .thenReturn(List.of(newest, older));

        assertThat(service.listOwn(owner.getEmail()))
                .extracting("id")
                .containsExactly(9L, 8L);
    }

    @Test
    void allowsProjectFileToAnAuthorizedProjectMember() {
        StoredFileEntity entity = storedFile(7L, "PROJECT");
        ProjectEntity project = mock(ProjectEntity.class);
        when(storedFileRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(entity));
        when(projectRepository.findByIdAndDeletedAtIsNull(4L)).thenReturn(Optional.of(project));
        when(projectAccessService.requireRead(project, owner.getEmail())).thenReturn(owner);
        when(fileStorage.download("drive-id")).thenReturn(new FileStorage.StoredFileContent(new byte[]{1, 2}));

        assertThat(service.canRead(7L, authentication(owner.getEmail()))).isTrue();
        assertThat(service.download(7L, authentication(owner.getEmail())).content()).containsExactly(1, 2);
    }

    @Test
    void deniesProjectFileToAnonymousUsers() {
        StoredFileEntity entity = storedFile(7L, "PROJECT");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.download(7L, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(fileStorage, never()).download(any());
    }

    @Test
    void allowsLabFileToAuthenticatedMember() {
        StoredFileEntity entity = storedFile(8L, "LAB");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(8L)).thenReturn(Optional.of(entity));
        when(fileStorage.download("drive-id")).thenReturn(new FileStorage.StoredFileContent(new byte[]{1, 2}));

        assertThat(service.download(8L, authentication("other@lab.test")).content()).containsExactly(1, 2);
    }

    @Test
    void allowsOwnerToDownloadTheirPrivateFile() {
        StoredFileEntity entity = storedFile(9L, "PRIVATE");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.of(entity));
        when(fileStorage.download("drive-id")).thenReturn(new FileStorage.StoredFileContent(new byte[]{4}));

        assertThat(service.download(9L, authentication(owner.getEmail())).content()).containsExactly(4);
    }

    @Test
    void deniesAnotherMemberFromDownloadingAPrivateFile() {
        StoredFileEntity entity = storedFile(9L, "PRIVATE");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.download(9L, authentication("other@lab.test")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(fileStorage, never()).download(any());
    }

    @Test
    void refusesToDeleteFileUsedAsAvatar() {
        StoredFileEntity entity = storedFile(10L, "PRIVATE");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(entity));
        when(memberProfileRepository.existsByAvatarFileId(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(10L, owner.getEmail(), authentication(owner.getEmail())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(fileStorage, never()).trash(any());
    }

    @Test
    void treatsLiveAvatarAsExternalToDocumentVisibilityChanges() {
        when(memberProfileRepository.existsByAvatarFileId(18L)).thenReturn(true);

        assertThat(service.hasExternalReferencesOutsideDocument(18L, 31L)).isTrue();
    }

    @Test
    void refusesToDeleteFileAttachedToAnActiveTask() {
        StoredFileEntity entity = storedFile(11L, "PROJECT");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(entity));
        when(taskAttachmentRepository.existsByFile_IdAndTask_DeletedAtIsNull(11L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(11L, owner.getEmail(), authentication(owner.getEmail())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(fileStorage, never()).trash(any());
    }

    @Test
    void refusesToDeleteFileUsedAsResearchFieldCover() {
        StoredFileEntity entity = storedFile(12L, "PUBLIC");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(12L)).thenReturn(Optional.of(entity));
        when(researchFieldRepository.existsByCoverFile_Id(12L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(12L, owner.getEmail(), authentication(owner.getEmail())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                            assertThat(exception.getReason()).isEqualTo("File is currently used as a research field cover");
                        });
        verify(fileStorage, never()).trash(any());
    }

    @Test
    void treatsResearchFieldCoverAsExternalToDocumentVisibilityChanges() {
        when(researchFieldRepository.existsByCoverFile_Id(19L)).thenReturn(true);

        assertThat(service.hasExternalReferencesOutsideDocument(19L, 31L)).isTrue();
    }

    @Test
    void allowsDeletionAfterResearchFieldCoverIsRemoved() {
        StoredFileEntity entity = storedFile(13L, "PUBLIC");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(13L)).thenReturn(Optional.of(entity));

        service.delete(13L, owner.getEmail(), authentication(owner.getEmail()));

        verify(fileStorage).trash("drive-id");
        verify(storedFileRepository).saveAndFlush(entity);
        assertThat(entity.getDeletedAt()).isNotNull();
    }

    @Test
    void activeAchievementAttachmentBlocksDeletionButDetachedOrDeletedAchievementDoesNot() {
        StoredFileEntity active = storedFile(14L, "PRIVATE");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(14L)).thenReturn(Optional.of(active));
        when(achievementFileRepository.existsActiveReferenceForActiveAchievement(14L)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(14L, owner.getEmail(), authentication(owner.getEmail())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(fileStorage, never()).trash("drive-id");

        StoredFileEntity detached = storedFile(15L, "PRIVATE");
        StoredFileEntity deletedAchievement = storedFile(16L, "PRIVATE");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(15L)).thenReturn(Optional.of(detached));
        when(storedFileRepository.findByIdAndDeletedAtIsNull(16L)).thenReturn(Optional.of(deletedAchievement));
        when(achievementFileRepository.existsActiveReferenceForActiveAchievement(15L)).thenReturn(false);
        when(achievementFileRepository.existsActiveReferenceForActiveAchievement(16L)).thenReturn(false);
        service.delete(15L, owner.getEmail(), authentication(owner.getEmail()));
        service.delete(16L, owner.getEmail(), authentication(owner.getEmail()));
        verify(fileStorage, org.mockito.Mockito.times(2)).trash("drive-id");
    }

    @Test
    void activeGalleryOwnershipBlocksGenericFileDeletion() {
        StoredFileEntity entity = storedFile(17L, "PRIVATE");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(17L)).thenReturn(Optional.of(entity));
        when(galleryItemRepository.existsByFile_IdAndDeletedAtIsNull(17L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(17L, owner.getEmail(), authentication(owner.getEmail())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(fileStorage, never()).trash(any());
    }

    @Test
    void allowsAdminToDownloadAnotherUsersPrivateFile() {
        StoredFileEntity entity = storedFile(12L, "PRIVATE");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(12L)).thenReturn(Optional.of(entity));
        when(fileStorage.download("drive-id")).thenReturn(new FileStorage.StoredFileContent(new byte[]{3}));

        Authentication admin = new UsernamePasswordAuthenticationToken(
                "admin@lab.test", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(service.download(12L, admin).content()).containsExactly(3);
    }

    @Test
    void standalonePublicFileCanBeDownloadedWithoutAuthentication() {
        StoredFileEntity file = storedFile(30L, "PUBLIC");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(30L)).thenReturn(Optional.of(file));
        when(fileStorage.download("drive-id")).thenReturn(new FileStorage.StoredFileContent(new byte[]{7}));

        assertThat(service.canRead(30L, null)).isTrue();
        assertThat(service.download(30L, null).content()).containsExactly(7);
    }

    @Test
    void privateFileRejectsAnonymousDirectDownload() {
        when(storedFileRepository.findByIdAndDeletedAtIsNull(31L))
                .thenReturn(Optional.of(storedFile(31L, "PRIVATE")));

        assertThat(service.canRead(31L, null)).isFalse();
        assertThatThrownBy(() -> service.download(31L, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(fileStorage, never()).download(any());
    }

    @Test
    void publicFileBoundToPublicProjectAllowsAnonymousDirectDownload() {
        StoredFileEntity file = storedFile(32L, "PUBLIC");
        file.setProjectId(4L);
        ProjectEntity project = publicProject();
        when(storedFileRepository.findByIdAndDeletedAtIsNull(32L)).thenReturn(Optional.of(file));
        lenient().when(projectRepository.findByIdAndDeletedAtIsNull(4L)).thenReturn(Optional.of(project));
        when(fileStorage.download("drive-id")).thenReturn(new FileStorage.StoredFileContent(new byte[]{8}));

        assertThat(project.getIsPublic()).isTrue();
        assertThat(service.download(32L, null).content()).containsExactly(8);
    }

    @Test
    void publicProjectFileMustStopAnonymousDirectAccessAfterProjectBecomesPrivate() {
        StoredFileEntity file = storedFile(33L, "PUBLIC");
        file.setProjectId(4L);
        ProjectEntity project = publicProject();
        project.updateCore(project.getCode(), project.getName(), null, null, project.getProjectType(),
                project.getStatus(), project.getStartDate(), null, null, false, false);
        when(storedFileRepository.findByIdAndDeletedAtIsNull(33L)).thenReturn(Optional.of(file));
        lenient().when(projectRepository.findByIdAndDeletedAtIsNull(4L)).thenReturn(Optional.of(project));

        assertThat(service.canRead(33L, null)).isFalse();
        assertThatThrownBy(() -> service.download(33L, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(fileStorage, never()).download(any());
    }

    @Test
    void authenticatedProjectReaderCanDownloadPublicFileAfterProjectBecomesPrivate() {
        StoredFileEntity file = storedFile(40L, "PUBLIC");
        file.setProjectId(4L);
        ProjectEntity project = publicProject();
        project.updateCore(project.getCode(), project.getName(), null, null, project.getProjectType(),
                project.getStatus(), project.getStartDate(), null, null, false, false);
        when(storedFileRepository.findByIdAndDeletedAtIsNull(40L)).thenReturn(Optional.of(file));
        when(projectRepository.findByIdAndDeletedAtIsNull(4L)).thenReturn(Optional.of(project));
        when(projectAccessService.requireRead(project, owner.getEmail())).thenReturn(owner);
        when(fileStorage.download("drive-id")).thenReturn(new FileStorage.StoredFileContent(new byte[]{9}));

        assertThat(service.canRead(40L, authentication(owner.getEmail()))).isTrue();
        assertThat(service.download(40L, authentication(owner.getEmail())).content()).containsExactly(9);
        verify(projectAccessService, org.mockito.Mockito.times(2)).requireRead(project, owner.getEmail());
    }

    @Test
    void unrelatedMemberCannotDownloadPublicFileAfterProjectBecomesPrivate() {
        StoredFileEntity file = storedFile(41L, "PUBLIC");
        file.setProjectId(4L);
        ProjectEntity project = publicProject();
        project.updateCore(project.getCode(), project.getName(), null, null, project.getProjectType(),
                project.getStatus(), project.getStartDate(), null, null, false, false);
        when(storedFileRepository.findByIdAndDeletedAtIsNull(41L)).thenReturn(Optional.of(file));
        lenient().when(projectRepository.findByIdAndDeletedAtIsNull(4L)).thenReturn(Optional.of(project));
        lenient().doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND))
                .when(projectAccessService).requireRead(project, "other@lab.test");

        assertThat(service.canRead(41L, authentication("other@lab.test"))).isFalse();
        assertThatThrownBy(() -> service.download(41L, authentication("other@lab.test")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(fileStorage, never()).download(any());
    }

    @Test
    void publicProjectFileMustStopAnonymousDirectAccessAfterProjectDeletion() {
        StoredFileEntity file = storedFile(34L, "PUBLIC");
        file.setProjectId(4L);
        ProjectEntity project = publicProject();
        project.softDelete();
        when(storedFileRepository.findByIdAndDeletedAtIsNull(34L)).thenReturn(Optional.of(file));
        lenient().when(projectRepository.findByIdAndDeletedAtIsNull(4L)).thenReturn(Optional.empty());

        assertThat(service.canRead(34L, null)).isFalse();
        assertThatThrownBy(() -> service.download(34L, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(fileStorage, never()).download(any());
    }

    @Test
    void failedStorageTrashLeavesFileMetadataActive() {
        StoredFileEntity file = storedFile(36L, "PRIVATE");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(36L)).thenReturn(Optional.of(file));
        doThrow(new StorageException("storage unavailable")).when(fileStorage).trash("drive-id");

        assertThatThrownBy(() -> service.delete(36L, owner.getEmail(), authentication(owner.getEmail())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        assertThat(file.getDeletedAt()).isNull();
        verify(storedFileRepository, never()).save(any());
    }

    @Test
    void failedDatabaseSaveRestoresStoredBytes() {
        StoredFileEntity file = storedFile(37L, "PRIVATE");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(37L)).thenReturn(Optional.of(file));
        when(storedFileRepository.saveAndFlush(file)).thenThrow(new IllegalStateException("database write failed"));
        AtomicBoolean trashed = new AtomicBoolean();
        doAnswer(invocation -> { trashed.set(true); return null; }).when(fileStorage).trash("drive-id");
        doAnswer(invocation -> { trashed.set(false); return null; }).when(fileStorage).restore("drive-id");

        assertThatThrownBy(() -> service.delete(37L, owner.getEmail(), authentication(owner.getEmail())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(trashed.get()).isFalse();
        verify(fileStorage).trash("drive-id");
        verify(fileStorage).restore("drive-id");
    }

    @Test
    void transactionRollbackAfterDeleteMustLeaveStoredBytesAvailable() {
        StoredFileEntity file = storedFile(38L, "PRIVATE");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(38L)).thenReturn(Optional.of(file));
        AtomicBoolean trashed = new AtomicBoolean();
        doAnswer(invocation -> { trashed.set(true); return null; }).when(fileStorage).trash("drive-id");
        doAnswer(invocation -> { trashed.set(false); return null; }).when(fileStorage).restore("drive-id");
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.delete(38L, owner.getEmail(), authentication(owner.getEmail()));
            verify(storedFileRepository).saveAndFlush(file);
            assertThat(trashed.get()).isTrue();
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            assertThat(trashed.get()).isFalse();
            verify(fileStorage).restore("drive-id");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void committedDeleteKeepsStorageTrashedAndMetadataSoftDeleted() {
        StoredFileEntity file = storedFile(42L, "PRIVATE");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(file));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.delete(42L, owner.getEmail(), authentication(owner.getEmail()));
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            }
            assertThat(file.getDeletedAt()).isNotNull();
            verify(fileStorage).trash("drive-id");
            verify(fileStorage, never()).restore("drive-id");
            verify(storedFileRepository).saveAndFlush(file);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void databaseFailureInsideOuterTransactionRestoresOnlyOnRollback() {
        StoredFileEntity file = storedFile(43L, "PRIVATE");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(43L)).thenReturn(Optional.of(file));
        when(storedFileRepository.saveAndFlush(file)).thenThrow(new IllegalStateException("database write failed"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> service.delete(43L, owner.getEmail(), authentication(owner.getEmail())))
                    .isInstanceOf(IllegalStateException.class);
            verify(fileStorage, never()).restore("drive-id");
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(fileStorage).restore("drive-id");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void restoreFailurePreservesOriginalDatabaseFailure() {
        StoredFileEntity file = storedFile(44L, "PRIVATE");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(44L)).thenReturn(Optional.of(file));
        IllegalStateException databaseFailure = new IllegalStateException("database write failed");
        when(storedFileRepository.saveAndFlush(file)).thenThrow(databaseFailure);
        doThrow(new StorageException("restore failed")).when(fileStorage).restore("drive-id");

        assertThatThrownBy(() -> service.delete(44L, owner.getEmail(), authentication(owner.getEmail())))
                .isSameAs(databaseFailure)
                .satisfies(failure -> assertThat(failure.getSuppressed()).hasSize(1));
    }

    @Test
    void currentDocumentReferenceRejectsGenericFileDeletion() {
        StoredFileEntity file = storedFile(39L, "PRIVATE");
        when(storedFileRepository.findByIdAndDeletedAtIsNull(39L)).thenReturn(Optional.of(file));
        when(documentRepository.existsByCurrentFile_IdAndDeletedAtIsNull(39L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(39L, owner.getEmail(), authentication(owner.getEmail())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(fileStorage, never()).trash(any());
    }

    private ProjectEntity publicProject() {
        ProjectEntity project = ProjectEntity.create("PUBLIC", "Public project", null, null,
                ProjectType.RESEARCH, null, ProjectStatus.IN_PROGRESS, LocalDate.of(2026, 1, 1),
                null, null, true, false, false, null);
        ReflectionTestUtils.setField(project, "id", 4L);
        return project;
    }

    private StoredFileEntity storedFile(Long id, String scope) {
        return StoredFileEntity.builder()
                .id(id)
                .ownerUser(owner)
                .projectId("PROJECT".equals(scope) ? 4L : null)
                .storageProvider("GOOGLE_DRIVE")
                .storageKey("drive-id")
                .originalName("file.png")
                .mimeType("image/png")
                .sizeBytes(2L)
                .accessScope(scope)
                .build();
    }

    private Authentication authentication(String email) {
        return new UsernamePasswordAuthenticationToken(email, "n/a", List.of());
    }

    private byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
    }
}
