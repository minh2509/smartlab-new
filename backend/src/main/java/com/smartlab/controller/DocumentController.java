package com.smartlab.controller;

import com.smartlab.dto.request.CreateDocumentRequest;
import com.smartlab.dto.request.CreateDocumentVersionRequest;
import com.smartlab.dto.response.DocumentResponse;
import com.smartlab.dto.response.DocumentVersionResponse;
import com.smartlab.service.DocumentService;
import com.smartlab.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@Validated
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Project documents", description = "Project documents and immutable uploaded versions.")
public class DocumentController {
    private final DocumentService documentService;

    @GetMapping("/projects/{projectId}/documents")
    @Operation(summary = "List readable documents in a project")
    public List<DocumentResponse> list(
            @PathVariable @Positive(message = "Project id must be positive") Long projectId,
            Authentication authentication
    ) {
        return documentService.list(projectId, authentication);
    }

    @PostMapping(value = "/projects/{projectId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a document with its first file version")
    public DocumentResponse create(
            @PathVariable @Positive(message = "Project id must be positive") Long projectId,
            @Valid @ModelAttribute CreateDocumentRequest request,
            Authentication authentication
    ) {
        return documentService.create(projectId, request, authentication);
    }

    @GetMapping("/documents/{documentId}/versions")
    @Operation(summary = "List readable versions of a document")
    public List<DocumentVersionResponse> listVersions(
            @PathVariable @Positive(message = "Document id must be positive") Long documentId,
            Authentication authentication
    ) {
        return documentService.listVersions(documentId, authentication);
    }

    @PostMapping(value = "/documents/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a new immutable document version")
    public DocumentVersionResponse addVersion(
            @PathVariable @Positive(message = "Document id must be positive") Long documentId,
            @Valid @ModelAttribute CreateDocumentVersionRequest request,
            Authentication authentication
    ) {
        return documentService.addVersion(documentId, request, authentication);
    }

    @GetMapping("/documents/{documentId}/download")
    @Operation(summary = "Download the current document file")
    public ResponseEntity<byte[]> download(
            @PathVariable @Positive(message = "Document id must be positive") Long documentId,
            Authentication authentication
    ) {
        FileService.DownloadedFile file = documentService.downloadCurrent(documentId, authentication);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(file.mimeType());
        } catch (IllegalArgumentException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.originalName(), StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(file.content());
    }

    @DeleteMapping("/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a document while retaining its file versions")
    public void delete(
            @PathVariable @Positive(message = "Document id must be positive") Long documentId,
            Authentication authentication
    ) {
        documentService.delete(documentId, authentication);
    }
}
