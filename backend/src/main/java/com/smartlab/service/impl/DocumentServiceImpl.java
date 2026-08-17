package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateDocumentRequest;
import com.smartlab.dto.request.CreateDocumentVersionRequest;
import com.smartlab.dto.response.DocumentResponse;
import com.smartlab.dto.response.DocumentUserResponse;
import com.smartlab.dto.response.DocumentVersionResponse;
import com.smartlab.dto.response.FileResponse;
import com.smartlab.entity.DocumentEntity;
import com.smartlab.entity.DocumentVersionEntity;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.ProjectMemberEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.FileAccessScope;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.repo.DocumentRepository;
import com.smartlab.repo.DocumentVersionRepository;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.StoredFileRepository;
import com.smartlab.service.AuditService;
import com.smartlab.service.DocumentService;
import com.smartlab.service.FileService;
import com.smartlab.service.NotificationRelated;
import com.smartlab.service.NotificationService;
import com.smartlab.service.ProjectAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {
    private static final String DOCUMENT = "DOCUMENT";
    private static final String DOCUMENT_VERSION = "DOCUMENT_VERSION";
    private static final String DOCUMENT_CREATED = "DOCUMENT_CREATED";
    private static final String DOCUMENT_VERSION_CREATED = "DOCUMENT_VERSION_CREATED";
    private static final String DOCUMENT_DELETED = "DOCUMENT_DELETED";
    private static final String PROJECT_DOCUMENT_CREATED = "PROJECT_DOCUMENT_CREATED";
    private static final String PROJECT_DOCUMENT_VERSION_CREATED = "PROJECT_DOCUMENT_VERSION_CREATED";
    private static final int TITLE_MAX_LENGTH = 255;
    private static final int DESCRIPTION_MAX_LENGTH = 20_000;
    private static final int NOTE_MAX_LENGTH = 5_000;

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final ProjectRepository projectRepository;
    private final StoredFileRepository storedFileRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessService projectAccessService;
    private final FileService fileService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> list(Long projectId, Authentication authentication) {
        ProjectEntity project = requireProject(projectId);
        projectAccessService.requireRead(project, currentEmail(authentication));
        return documentRepository.findActiveByProjectId(projectId).stream()
                .filter(document -> fileService.canRead(document.getCurrentFile().getId(), authentication))
                .map(document -> toDocumentResponse(
                        document,
                        documentVersionRepository.findMaxVersionNo(document.getId()),
                        authentication
                ))
                .toList();
    }

    @Override
    @Transactional
    public DocumentResponse create(
            Long projectId,
            CreateDocumentRequest request,
            Authentication authentication
    ) {
        requireRequest(request);
        ProjectEntity project = requireProjectForUpdate(projectId);
        UserEntity actor = projectAccessService.requireManage(project, currentEmail(authentication));
        String title = normalizeRequired(request.getTitle(), TITLE_MAX_LENGTH, "Document title");
        String description = normalizeOptional(
                request.getDescription(),
                DESCRIPTION_MAX_LENGTH,
                "Document description"
        );
        String note = normalizeOptional(request.getNote(), NOTE_MAX_LENGTH, "Version note");
        String scope = FileAccessScope.from(request.getAccessScope(), FileAccessScope.PROJECT).name();

        FileResponse uploaded = fileService.uploadForProject(
                request.getFile(),
                scope,
                description,
                actor.getEmail(),
                project.getId()
        );
        StoredFileEntity file = requireStoredFile(uploaded.getId());
        DocumentEntity document = documentRepository.saveAndFlush(
                DocumentEntity.create(project, title, description, file, actor)
        );
        DocumentVersionEntity version = documentVersionRepository.saveAndFlush(
                DocumentVersionEntity.create(document, file, 1, actor, note)
        );

        auditService.log(DOCUMENT_CREATED, DOCUMENT, document.getId().toString(), null, documentSnapshot(document));
        auditService.log(
                DOCUMENT_VERSION_CREATED,
                DOCUMENT_VERSION,
                version.getId().toString(),
                null,
                versionSnapshot(version)
        );
        notifyActiveReadableProjectMembers(
                project,
                file,
                actor,
                PROJECT_DOCUMENT_CREATED,
                "A project document was added",
                document.getId()
        );
        return toDocumentResponse(document, 1, authentication);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentVersionResponse> listVersions(Long documentId, Authentication authentication) {
        DocumentEntity document = requireDocument(documentId);
        projectAccessService.requireRead(document.getProject(), currentEmail(authentication));
        fileService.describe(document.getCurrentFile().getId(), authentication);
        return documentVersionRepository.findReadableCandidatesByDocumentId(documentId).stream()
                .filter(version -> fileService.canRead(version.getFile().getId(), authentication))
                .map(version -> toVersionResponse(version, authentication))
                .toList();
    }

    @Override
    @Transactional
    public DocumentVersionResponse addVersion(
            Long documentId,
            CreateDocumentVersionRequest request,
            Authentication authentication
    ) {
        requireRequest(request);
        DocumentEntity document = requireDocumentForUpdate(documentId);
        fileService.describe(document.getCurrentFile().getId(), authentication);
        UserEntity actor = projectAccessService.requireManage(
                document.getProject(),
                currentEmail(authentication)
        );
        String note = normalizeOptional(request.getNote(), NOTE_MAX_LENGTH, "Version note");
        String scope = FileAccessScope.from(
                request.getAccessScope(),
                FileAccessScope.from(document.getCurrentFile().getAccessScope(), FileAccessScope.PROJECT)
        ).name();

        FileResponse uploaded = fileService.uploadForProject(
                request.getFile(),
                scope,
                note,
                actor.getEmail(),
                document.getProject().getId()
        );
        StoredFileEntity file = requireStoredFile(uploaded.getId());
        int nextVersionNo = documentVersionRepository.findMaxVersionNo(documentId) + 1;
        DocumentVersionEntity version = documentVersionRepository.saveAndFlush(
                DocumentVersionEntity.create(document, file, nextVersionNo, actor, note)
        );
        Long previousFileId = document.getCurrentFile().getId();
        document.useFile(file);
        documentRepository.saveAndFlush(document);

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("currentFileId", previousFileId);
        Map<String, Object> after = documentSnapshot(document);
        after.put("versionNo", nextVersionNo);
        auditService.log(DOCUMENT_VERSION_CREATED, DOCUMENT_VERSION, version.getId().toString(), null,
                versionSnapshot(version));
        auditService.log("DOCUMENT_CURRENT_VERSION_UPDATED", DOCUMENT, document.getId().toString(), before, after);
        notifyActiveReadableProjectMembers(
                document.getProject(),
                file,
                actor,
                PROJECT_DOCUMENT_VERSION_CREATED,
                "A new project document version was added",
                document.getId()
        );
        return toVersionResponse(version, authentication);
    }

    @Override
    @Transactional(readOnly = true)
    public FileService.DownloadedFile downloadCurrent(Long documentId, Authentication authentication) {
        DocumentEntity document = requireDocument(documentId);
        projectAccessService.requireRead(document.getProject(), currentEmail(authentication));
        return fileService.download(document.getCurrentFile().getId(), authentication);
    }

    @Override
    @Transactional
    public void delete(Long documentId, Authentication authentication) {
        DocumentEntity document = requireDocumentForUpdate(documentId);
        fileService.describe(document.getCurrentFile().getId(), authentication);
        projectAccessService.requireManage(document.getProject(), currentEmail(authentication));
        Map<String, Object> before = documentSnapshot(document);
        document.softDelete();
        DocumentEntity saved = documentRepository.saveAndFlush(document);
        auditService.log(DOCUMENT_DELETED, DOCUMENT, saved.getId().toString(), before, documentSnapshot(saved));
    }

    private ProjectEntity requireProject(Long projectId) {
        validatePositiveId(projectId, "Project id");
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId));
    }

    private ProjectEntity requireProjectForUpdate(Long projectId) {
        validatePositiveId(projectId, "Project id");
        return projectRepository.findActiveByIdForUpdate(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId));
    }

    private DocumentEntity requireDocument(Long documentId) {
        validatePositiveId(documentId, "Document id");
        return documentRepository.findActiveById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + documentId));
    }

    private DocumentEntity requireDocumentForUpdate(Long documentId) {
        validatePositiveId(documentId, "Document id");
        return documentRepository.findActiveByIdForUpdate(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + documentId));
    }

    private StoredFileEntity requireStoredFile(Long fileId) {
        return storedFileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Uploaded file was not persisted"));
    }

    private DocumentResponse toDocumentResponse(
            DocumentEntity document,
            int currentVersionNo,
            Authentication authentication
    ) {
        return DocumentResponse.builder()
                .id(document.getId())
                .projectId(document.getProject().getId())
                .title(document.getTitle())
                .description(document.getDescription())
                .currentFile(fileService.describe(document.getCurrentFile().getId(), authentication))
                .currentVersionNo(currentVersionNo)
                .createdBy(toUserResponse(document.getCreatedBy()))
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    private DocumentVersionResponse toVersionResponse(
            DocumentVersionEntity version,
            Authentication authentication
    ) {
        return DocumentVersionResponse.builder()
                .id(version.getId())
                .documentId(version.getDocument().getId())
                .versionNo(version.getVersionNo())
                .file(fileService.describe(version.getFile().getId(), authentication))
                .uploadedBy(toUserResponse(version.getUploadedBy()))
                .note(version.getNote())
                .createdAt(version.getCreatedAt())
                .build();
    }

    private DocumentUserResponse toUserResponse(UserEntity user) {
        if (user == null) return null;
        return DocumentUserResponse.builder()
                .userId(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }

    private String currentEmail(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }

    private void requireRequest(Object request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document request is required");
        }
    }

    private String normalizeRequired(String value, int maxLength, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength, String label) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    private void validatePositiveId(Long value, String label) {
        if (value == null || value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must be positive");
        }
    }

    private void notifyActiveReadableProjectMembers(
            ProjectEntity project,
            StoredFileEntity file,
            UserEntity actor,
            String type,
            String message,
            Long documentId
    ) {
        if (FileAccessScope.PRIVATE.name().equals(file.getAccessScope())) {
            return;
        }

        Set<Long> notifiedUserIds = new LinkedHashSet<>();
        projectMemberRepository.findMembersForDisplay(project.getId(), ProjectMemberStatus.ACTIVE).stream()
                .map(ProjectMemberEntity::getUser)
                .filter(member -> member != null && !member.getId().equals(actor.getId()))
                .filter(member -> notifiedUserIds.add(member.getId()))
                .forEach(member -> notifyIfReadable(project, actor, member, type, message, documentId));
    }

    private void notifyIfReadable(
            ProjectEntity project,
            UserEntity actor,
            UserEntity member,
            String type,
            String message,
            Long documentId
    ) {
        try {
            projectAccessService.requireRead(project, member.getEmail());
        } catch (ResponseStatusException exception) {
            return;
        }
        notificationService.notify(
                member.getId(),
                type,
                message,
                new NotificationRelated(
                        actor.getId(),
                        DOCUMENT,
                        documentId,
                        "/admin/projects?projectId=" + project.getId() + "&tab=documents"
                ),
                Instant.now()
        );
    }

    private Map<String, Object> documentSnapshot(DocumentEntity document) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("projectId", document.getProject().getId());
        value.put("title", document.getTitle());
        value.put("description", document.getDescription());
        value.put("currentFileId", document.getCurrentFile().getId());
        value.put("deletedAt", document.getDeletedAt() == null ? null : document.getDeletedAt().toString());
        return value;
    }

    private Map<String, Object> versionSnapshot(DocumentVersionEntity version) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("documentId", version.getDocument().getId());
        value.put("fileId", version.getFile().getId());
        value.put("versionNo", version.getVersionNo());
        value.put("note", version.getNote());
        return value;
    }
}
