package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateDocumentRequest;
import com.smartlab.dto.request.CreateDocumentVersionRequest;
import com.smartlab.dto.response.DocumentResponse;
import com.smartlab.dto.response.DocumentVersionResponse;
import com.smartlab.dto.response.FileResponse;
import com.smartlab.entity.DocumentEntity;
import com.smartlab.entity.DocumentVersionEntity;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.ProjectMemberEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import com.smartlab.repo.DocumentRepository;
import com.smartlab.repo.DocumentVersionRepository;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.StoredFileRepository;
import com.smartlab.service.AuditService;
import com.smartlab.service.FileService;
import com.smartlab.service.NotificationRelated;
import com.smartlab.service.NotificationService;
import com.smartlab.service.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {
    private static final String EMAIL = "leader@smartlab.test";

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentVersionRepository documentVersionRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private StoredFileRepository storedFileRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private FileService fileService;
    @Mock(answer = Answers.RETURNS_DEFAULTS) private AuditService auditService;
    @Mock private NotificationService notificationService;
    @InjectMocks private DocumentServiceImpl service;

    private Authentication authentication;
    private UserEntity actor;
    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        authentication = new UsernamePasswordAuthenticationToken(EMAIL, null, List.of());
        actor = UserEntity.builder()
                .id(11L)
                .userId("leader-user")
                .name("Leader")
                .email(EMAIL)
                .isActive(true)
                .isAccountVerified(true)
                .build();
        project = ProjectEntity.create(
                "SL-DOC",
                "Documents",
                null,
                null,
                ProjectType.RESEARCH,
                actor,
                ProjectStatus.IN_PROGRESS,
                LocalDate.now(),
                null,
                null,
                false,
                false,
                actor
        );
        setField(project, "id", 7L);
    }

    @Test
    void createsDocumentAndFirstImmutableVersionWithProjectScopeByDefault() {
        MockMultipartFile upload = textFile("proposal.txt", "proposal");
        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setFile(upload);
        request.setTitle("  Proposal  ");
        request.setDescription("  Initial document  ");
        request.setNote("  version one  ");
        StoredFileEntity storedFile = storedFile(101L, "PROJECT", actor);
        FileResponse fileResponse = fileResponse(101L, "PROJECT");

        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(projectAccessService.requireManage(project, EMAIL)).thenReturn(actor);
        when(fileService.uploadForProject(upload, "PROJECT", "Initial document", EMAIL, 7L))
                .thenReturn(fileResponse);
        when(storedFileRepository.findByIdAndDeletedAtIsNull(101L)).thenReturn(Optional.of(storedFile));
        when(documentRepository.saveAndFlush(any(DocumentEntity.class))).thenAnswer(invocation -> {
            DocumentEntity document = invocation.getArgument(0);
            setField(document, "id", 31L);
            return document;
        });
        when(documentVersionRepository.saveAndFlush(any(DocumentVersionEntity.class))).thenAnswer(invocation -> {
            DocumentVersionEntity version = invocation.getArgument(0);
            setField(version, "id", 41L);
            return version;
        });
        when(fileService.describe(101L, authentication)).thenReturn(fileResponse);

        DocumentResponse response = service.create(7L, request, authentication);

        assertThat(response.getId()).isEqualTo(31L);
        assertThat(response.getProjectId()).isEqualTo(7L);
        assertThat(response.getTitle()).isEqualTo("Proposal");
        assertThat(response.getDescription()).isEqualTo("Initial document");
        assertThat(response.getCurrentVersionNo()).isEqualTo(1);
        assertThat(response.getCurrentFile().getId()).isEqualTo(101L);
        verify(documentVersionRepository).saveAndFlush(any(DocumentVersionEntity.class));
        verify(auditService).log(eq("DOCUMENT_CREATED"), eq("DOCUMENT"), eq("31"), eq(null), any());
    }

    @Test
    void projectDocumentCreateNotifiesReadableActiveMembersOnceWithDocumentMetadata() {
        UserEntity recipient = user(12L, "member@smartlab.test");
        createDocumentWithScope("PROJECT");
        when(projectMemberRepository.findMembersForDisplay(7L, com.smartlab.enums.ProjectMemberStatus.ACTIVE))
                .thenReturn(List.of(ProjectMemberEntity.createMember(project, actor), ProjectMemberEntity.createMember(project, recipient),
                        ProjectMemberEntity.createMember(project, recipient)));

        service.create(7L, createRequest("PROJECT"), authentication);

        verify(projectAccessService).requireRead(project, recipient.getEmail());
        verify(notificationService).notify(
                eq(12L), eq("PROJECT_DOCUMENT_CREATED"), eq("A project document was added"),
                eq(new NotificationRelated(11L, "DOCUMENT", 31L, "/admin/projects?projectId=7&tab=documents")), isA(java.time.Instant.class)
        );
        verify(notificationService, never()).notify(eq(11L), any(), any(), any(), any());
    }

    @Test
    void labVersionNotifiesOnlyReadableActiveProjectMembersWithDocumentId() {
        UserEntity readable = user(12L, "readable@smartlab.test");
        UserEntity rejected = user(13L, "rejected@smartlab.test");
        StoredFileEntity oldFile = storedFile(101L, "LAB", actor);
        DocumentEntity document = document(31L, oldFile);
        configureVersionCreation(document, "LAB");
        when(projectMemberRepository.findMembersForDisplay(7L, com.smartlab.enums.ProjectMemberStatus.ACTIVE))
                .thenReturn(List.of(ProjectMemberEntity.createMember(project, readable), ProjectMemberEntity.createMember(project, rejected)));
        when(projectAccessService.requireRead(project, readable.getEmail())).thenReturn(readable);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "not readable"))
                .when(projectAccessService).requireRead(project, rejected.getEmail());

        service.addVersion(31L, versionRequest("LAB"), authentication);

        verify(notificationService).notify(
                eq(12L), eq("PROJECT_DOCUMENT_VERSION_CREATED"), eq("A new project document version was added"),
                eq(new NotificationRelated(11L, "DOCUMENT", 31L, "/admin/projects?projectId=7&tab=documents")), isA(java.time.Instant.class)
        );
        verify(notificationService, never()).notify(eq(13L), any(), any(), any(), any());
    }

    @Test
    void privateDocumentAndVersionDoNotBroadcast() {
        createDocumentWithScope("PRIVATE");
        service.create(7L, createRequest("PRIVATE"), authentication);

        StoredFileEntity oldFile = storedFile(101L, "PRIVATE", actor);
        DocumentEntity document = document(31L, oldFile);
        configureVersionCreation(document, "PRIVATE");
        service.addVersion(31L, versionRequest("PRIVATE"), authentication);

        verifyNoInteractions(projectMemberRepository, notificationService);
    }

    @Test
    void notificationFailurePropagatesAfterSuccessfulDocumentMutation() {
        UserEntity recipient = user(12L, "member@smartlab.test");
        createDocumentWithScope("PUBLIC");
        when(projectMemberRepository.findMembersForDisplay(7L, com.smartlab.enums.ProjectMemberStatus.ACTIVE))
                .thenReturn(List.of(ProjectMemberEntity.createMember(project, recipient)));
        doThrow(new IllegalStateException("notification unavailable")).when(notificationService).notify(
                any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.create(7L, createRequest("PUBLIC"), authentication))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("notification unavailable");

        verify(auditService).log(eq("DOCUMENT_CREATED"), eq("DOCUMENT"), eq("31"), eq(null), any());
    }

    @Test
    void addingVersionLocksDocumentInheritsScopeAndMovesCurrentFile() {
        StoredFileEntity oldFile = storedFile(101L, "LAB", actor);
        StoredFileEntity newFile = storedFile(102L, "LAB", actor);
        DocumentEntity document = document(31L, oldFile);
        MockMultipartFile upload = textFile("proposal-v3.txt", "version three");
        CreateDocumentVersionRequest request = new CreateDocumentVersionRequest();
        request.setFile(upload);
        request.setNote("  revised  ");
        FileResponse responseFile = fileResponse(102L, "LAB");

        when(documentRepository.findActiveByIdForUpdate(31L)).thenReturn(Optional.of(document));
        when(fileService.describe(101L, authentication)).thenReturn(fileResponse(101L, "LAB"));
        when(projectAccessService.requireManage(project, EMAIL)).thenReturn(actor);
        when(fileService.uploadForProject(upload, "LAB", "revised", EMAIL, 7L)).thenReturn(responseFile);
        when(storedFileRepository.findByIdAndDeletedAtIsNull(102L)).thenReturn(Optional.of(newFile));
        when(documentVersionRepository.findMaxVersionNo(31L)).thenReturn(2);
        when(documentVersionRepository.saveAndFlush(any(DocumentVersionEntity.class))).thenAnswer(invocation -> {
            DocumentVersionEntity version = invocation.getArgument(0);
            setField(version, "id", 43L);
            return version;
        });
        when(documentRepository.saveAndFlush(document)).thenReturn(document);
        when(fileService.describe(102L, authentication)).thenReturn(responseFile);

        DocumentVersionResponse response = service.addVersion(31L, request, authentication);

        assertThat(response.getVersionNo()).isEqualTo(3);
        assertThat(response.getFile().getId()).isEqualTo(102L);
        assertThat(response.getNote()).isEqualTo("revised");
        assertThat(document.getCurrentFile()).isSameAs(newFile);
        verify(documentRepository).findActiveByIdForUpdate(31L);
        verify(auditService).log(eq("DOCUMENT_CURRENT_VERSION_UPDATED"), eq("DOCUMENT"), eq("31"), any(), any());
    }

    @Test
    void listOnlyReturnsDocumentsWhoseCurrentFileIsReadable() {
        StoredFileEntity readableFile = storedFile(101L, "PROJECT", actor);
        StoredFileEntity privateFile = storedFile(102L, "PRIVATE", actor);
        DocumentEntity readable = document(31L, readableFile);
        DocumentEntity hidden = document(32L, privateFile);

        when(projectRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(project));
        when(documentRepository.findActiveByProjectId(7L)).thenReturn(List.of(readable, hidden));
        when(fileService.canRead(101L, authentication)).thenReturn(true);
        when(fileService.canRead(102L, authentication)).thenReturn(false);
        when(documentVersionRepository.findMaxVersionNo(31L)).thenReturn(4);
        when(fileService.describe(101L, authentication)).thenReturn(fileResponse(101L, "PROJECT"));

        List<DocumentResponse> result = service.list(7L, authentication);

        assertThat(result).extracting(DocumentResponse::getId).containsExactly(31L);
        assertThat(result.getFirst().getCurrentVersionNo()).isEqualTo(4);
        verify(projectAccessService).requireRead(project, EMAIL);
        verify(documentVersionRepository, never()).findMaxVersionNo(32L);
    }

    @Test
    void listChecksProjectReadBeforeQueryingDocumentMetadata() {
        when(projectRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(project));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: 7"))
                .when(projectAccessService).requireRead(project, EMAIL);

        assertThatThrownBy(() -> service.list(7L, authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(documentRepository, never()).findActiveByProjectId(any());
    }

    @Test
    void versionsRequireCurrentDocumentAccessAndThenFilterIndividualFiles() {
        StoredFileEntity currentFile = storedFile(102L, "PROJECT", actor);
        StoredFileEntity oldReadableFile = storedFile(101L, "LAB", actor);
        DocumentEntity document = document(31L, currentFile);
        DocumentVersionEntity current = version(42L, document, currentFile, 2);
        DocumentVersionEntity old = version(41L, document, oldReadableFile, 1);

        when(documentRepository.findActiveById(31L)).thenReturn(Optional.of(document));
        when(fileService.describe(102L, authentication)).thenReturn(fileResponse(102L, "PROJECT"));
        when(documentVersionRepository.findReadableCandidatesByDocumentId(31L)).thenReturn(List.of(current, old));
        when(fileService.canRead(102L, authentication)).thenReturn(true);
        when(fileService.canRead(101L, authentication)).thenReturn(false);

        List<DocumentVersionResponse> result = service.listVersions(31L, authentication);

        assertThat(result).extracting(DocumentVersionResponse::getVersionNo).containsExactly(2);
        verify(projectAccessService).requireRead(project, EMAIL);
    }

    @Test
    void persistenceFailurePropagatesAndRollsBackBeforeCreatingVersion() {
        MockMultipartFile upload = textFile("proposal.txt", "proposal");
        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setFile(upload);
        request.setTitle("Proposal");
        StoredFileEntity storedFile = storedFile(101L, "PROJECT", actor);

        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(projectAccessService.requireManage(project, EMAIL)).thenReturn(actor);
        when(fileService.uploadForProject(upload, "PROJECT", null, EMAIL, 7L))
                .thenReturn(fileResponse(101L, "PROJECT"));
        when(storedFileRepository.findByIdAndDeletedAtIsNull(101L)).thenReturn(Optional.of(storedFile));
        when(documentRepository.saveAndFlush(any(DocumentEntity.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.create(7L, request, authentication))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(documentVersionRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsMissingDocumentBeforeUploadingVersion() {
        MockMultipartFile upload = textFile("missing.txt", "missing");
        CreateDocumentVersionRequest request = new CreateDocumentVersionRequest();
        request.setFile(upload);
        when(documentRepository.findActiveByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addVersion(404L, request, authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(fileService, never()).uploadForProject(any(), any(), any(), any(), any());
    }

    @Test
    void anotherLeaderCannotReplaceAPrivateDocumentTheyCannotRead() {
        StoredFileEntity privateFile = storedFile(101L, "PRIVATE", actor);
        DocumentEntity document = document(31L, privateFile);
        CreateDocumentVersionRequest request = new CreateDocumentVersionRequest();
        request.setFile(textFile("private-v2.txt", "secret"));
        when(documentRepository.findActiveByIdForUpdate(31L)).thenReturn(Optional.of(document));
        when(fileService.describe(101L, authentication))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this file"));

        assertThatThrownBy(() -> service.addVersion(31L, request, authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(projectAccessService, never()).requireManage(any(), any());
        verify(fileService, never()).uploadForProject(any(), any(), any(), any(), any());
    }

    @Test
    void anotherLeaderCannotDeleteAPrivateDocumentTheyCannotRead() {
        StoredFileEntity privateFile = storedFile(101L, "PRIVATE", actor);
        DocumentEntity document = document(31L, privateFile);
        when(documentRepository.findActiveByIdForUpdate(31L)).thenReturn(Optional.of(document));
        when(fileService.describe(101L, authentication))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this file"));

        assertThatThrownBy(() -> service.delete(31L, authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        assertThat(document.getDeletedAt()).isNull();
        verify(projectAccessService, never()).requireManage(any(), any());
        verify(documentRepository, never()).saveAndFlush(document);
    }

    private DocumentEntity document(Long id, StoredFileEntity file) {
        DocumentEntity document = DocumentEntity.create(project, "Proposal", "Description", file, actor);
        setField(document, "id", id);
        return document;
    }

    private DocumentVersionEntity version(
            Long id,
            DocumentEntity document,
            StoredFileEntity file,
            int versionNo
    ) {
        DocumentVersionEntity version = DocumentVersionEntity.create(document, file, versionNo, actor, "note");
        setField(version, "id", id);
        return version;
    }

    private static StoredFileEntity storedFile(Long id, String scope, UserEntity owner) {
        return StoredFileEntity.builder()
                .id(id)
                .ownerUser(owner)
                .projectId(7L)
                .storageProvider("GOOGLE_DRIVE")
                .storageKey("key-" + id)
                .originalName("file-" + id + ".txt")
                .mimeType("text/plain")
                .sizeBytes(10L)
                .accessScope(scope)
                .build();
    }

    private static FileResponse fileResponse(Long id, String scope) {
        return FileResponse.builder()
                .id(id)
                .originalName("file-" + id + ".txt")
                .mimeType("text/plain")
                .sizeBytes(10L)
                .accessScope(scope)
                .build();
    }

    private static MockMultipartFile textFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes());
    }

    private CreateDocumentRequest createRequest(String scope) {
        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setFile(textFile("proposal.txt", "proposal"));
        request.setTitle("Proposal");
        request.setAccessScope(scope);
        return request;
    }

    private CreateDocumentVersionRequest versionRequest(String scope) {
        CreateDocumentVersionRequest request = new CreateDocumentVersionRequest();
        request.setFile(textFile("proposal-v2.txt", "version two"));
        request.setAccessScope(scope);
        return request;
    }

    private void createDocumentWithScope(String scope) {
        StoredFileEntity storedFile = storedFile(101L, scope, actor);
        FileResponse fileResponse = fileResponse(101L, scope);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(projectAccessService.requireManage(project, EMAIL)).thenReturn(actor);
        when(fileService.uploadForProject(any(), eq(scope), any(), eq(EMAIL), eq(7L))).thenReturn(fileResponse);
        when(storedFileRepository.findByIdAndDeletedAtIsNull(101L)).thenReturn(Optional.of(storedFile));
        when(documentRepository.saveAndFlush(any(DocumentEntity.class))).thenAnswer(invocation -> {
            DocumentEntity document = invocation.getArgument(0);
            setField(document, "id", 31L);
            return document;
        });
        when(documentVersionRepository.saveAndFlush(any(DocumentVersionEntity.class))).thenAnswer(invocation -> {
            DocumentVersionEntity version = invocation.getArgument(0);
            setField(version, "id", 41L);
            return version;
        });
        lenient().when(fileService.describe(101L, authentication)).thenReturn(fileResponse);
    }

    private void configureVersionCreation(DocumentEntity document, String scope) {
        StoredFileEntity newFile = storedFile(102L, scope, actor);
        FileResponse responseFile = fileResponse(102L, scope);
        when(documentRepository.findActiveByIdForUpdate(31L)).thenReturn(Optional.of(document));
        when(fileService.describe(101L, authentication)).thenReturn(fileResponse(101L, document.getCurrentFile().getAccessScope()));
        when(projectAccessService.requireManage(project, EMAIL)).thenReturn(actor);
        when(fileService.uploadForProject(any(), eq(scope), any(), eq(EMAIL), eq(7L))).thenReturn(responseFile);
        when(storedFileRepository.findByIdAndDeletedAtIsNull(102L)).thenReturn(Optional.of(newFile));
        when(documentVersionRepository.findMaxVersionNo(31L)).thenReturn(1);
        when(documentVersionRepository.saveAndFlush(any(DocumentVersionEntity.class))).thenAnswer(invocation -> {
            DocumentVersionEntity version = invocation.getArgument(0);
            setField(version, "id", 43L);
            return version;
        });
        when(documentRepository.saveAndFlush(document)).thenReturn(document);
        when(fileService.describe(102L, authentication)).thenReturn(responseFile);
    }

    private static UserEntity user(Long id, String email) {
        return UserEntity.builder()
                .id(id)
                .userId("user-" + id)
                .name("User " + id)
                .email(email)
                .isActive(true)
                .isAccountVerified(true)
                .build();
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
