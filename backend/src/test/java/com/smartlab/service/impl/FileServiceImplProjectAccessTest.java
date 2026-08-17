package com.smartlab.service.impl;

import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.repo.DocumentRepository;
import com.smartlab.repo.DocumentVersionRepository;
import com.smartlab.repo.MemberProfileRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.StoredFileRepository;
import com.smartlab.repo.TaskAttachmentRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.ProjectAccessService;
import com.smartlab.storage.FileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceImplProjectAccessTest {
    private static final String EMAIL = "member@smartlab.test";

    @Mock private StoredFileRepository storedFileRepository;
    @Mock private UserRepository userRepository;
    @Mock private FileStorage fileStorage;
    @Mock private MemberProfileRepository memberProfileRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentVersionRepository documentVersionRepository;
    @Mock private TaskAttachmentRepository taskAttachmentRepository;
    @InjectMocks private FileServiceImpl service;

    @Test
    void projectFileDownloadDelegatesToEffectiveProjectReadPolicy() {
        UserEntity member = user();
        StoredFileEntity file = file(22L, "PROJECT", member);
        ProjectEntity project = org.mockito.Mockito.mock(ProjectEntity.class);
        Authentication authentication = authentication();
        byte[] bytes = "project document".getBytes(StandardCharsets.UTF_8);

        when(storedFileRepository.findByIdAndDeletedAtIsNull(22L)).thenReturn(Optional.of(file));
        when(projectRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(project));
        when(projectAccessService.requireRead(project, EMAIL)).thenReturn(member);
        when(fileStorage.download("drive-key"))
                .thenReturn(new FileStorage.StoredFileContent(bytes));

        var downloaded = service.download(22L, authentication);

        assertThat(downloaded.content()).isEqualTo(bytes);
        verify(projectAccessService).requireRead(project, EMAIL);
    }

    @Test
    void projectFileDownloadRejectsMemberWhenEffectiveProjectReadIsDenied() {
        StoredFileEntity file = file(22L, "PROJECT", user());
        ProjectEntity project = org.mockito.Mockito.mock(ProjectEntity.class);
        Authentication authentication = authentication();

        when(storedFileRepository.findByIdAndDeletedAtIsNull(22L)).thenReturn(Optional.of(file));
        when(projectRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(project));
        when(projectAccessService.requireRead(project, EMAIL))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: 7"));

        assertThatThrownBy(() -> service.download(22L, authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(fileStorage, never()).download("drive-key");
    }

    @Test
    void projectFileUploadRejectsMemberBeforeWritingStorageWhenProjectReadIsDenied() {
        ProjectEntity project = org.mockito.Mockito.mock(ProjectEntity.class);
        MockMultipartFile upload = new MockMultipartFile(
                "file", "document.txt", "text/plain", "document".getBytes(StandardCharsets.UTF_8)
        );
        when(projectRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(project));
        when(projectAccessService.requireRead(project, EMAIL))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: 7"));

        assertThatThrownBy(() -> service.uploadForProject(upload, "PROJECT", null, EMAIL, 7L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(fileStorage, never()).upload(any(), any(), any(), any());
    }

    @Test
    void taskProjectUploadDoesNotRequireProjectReadAfterTaskAuthorization() throws Exception {
        UserEntity owner = user();
        MockMultipartFile upload = new MockMultipartFile(
                "file", "document.txt", "text/plain", "document".getBytes(StandardCharsets.UTF_8)
        );
        ReflectionTestUtils.setField(service, "maxFileSizeBytes", 1024L);
        ReflectionTestUtils.setField(service, "storageProvider", "google-drive");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(owner));
        when(fileStorage.upload("document.txt", "text/plain", upload.getBytes(), null))
                .thenReturn(new FileStorage.StoredFile("drive-key", null));
        when(storedFileRepository.saveAndFlush(any(StoredFileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.uploadForTaskProject(upload, null, EMAIL, 7L);

        assertThat(response.getAccessScope()).isEqualTo("PROJECT");
        verify(projectAccessService, never()).requireRead(any(), any());
    }

    @Test
    void regularFileDeleteCannotBreakRetainedDocumentVersions() {
        StoredFileEntity file = file(22L, "PRIVATE", user());
        Authentication authentication = authentication();
        when(storedFileRepository.findByIdAndDeletedAtIsNull(22L)).thenReturn(Optional.of(file));
        when(documentVersionRepository.existsByFile_Id(22L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(22L, EMAIL, authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(fileStorage, never()).trash("drive-key");
    }

    @Test
    void successfulFileRowInsertRegistersCloudCleanupForOuterTransactionRollback() throws Exception {
        UserEntity owner = user();
        ProjectEntity project = org.mockito.Mockito.mock(ProjectEntity.class);
        MockMultipartFile upload = new MockMultipartFile(
                "file", "document.txt", "text/plain", "document".getBytes(StandardCharsets.UTF_8)
        );
        ReflectionTestUtils.setField(service, "maxFileSizeBytes", 1024L);
        ReflectionTestUtils.setField(service, "storageProvider", "google-drive");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(owner));
        when(projectRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(project));
        when(projectAccessService.requireRead(project, EMAIL)).thenReturn(owner);
        when(fileStorage.upload("document.txt", "text/plain", upload.getBytes(), null))
                .thenReturn(new FileStorage.StoredFile("drive-key", null));
        when(storedFileRepository.saveAndFlush(any(StoredFileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.uploadForProject(upload, "PROJECT", null, EMAIL, 7L);

            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
            verify(fileStorage, never()).trash("drive-key");

            TransactionSynchronizationManager.getSynchronizations().getFirst()
                    .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            verify(fileStorage).trash("drive-key");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(EMAIL, null, List.of());
    }

    private static UserEntity user() {
        return UserEntity.builder()
                .id(11L)
                .userId("member-user")
                .name("Member")
                .email(EMAIL)
                .isActive(true)
                .isAccountVerified(true)
                .build();
    }

    private static StoredFileEntity file(Long id, String scope, UserEntity owner) {
        return StoredFileEntity.builder()
                .id(id)
                .ownerUser(owner)
                .projectId(7L)
                .storageProvider("GOOGLE_DRIVE")
                .storageKey("drive-key")
                .originalName("document.txt")
                .mimeType("text/plain")
                .sizeBytes(16L)
                .accessScope(scope)
                .build();
    }
}
