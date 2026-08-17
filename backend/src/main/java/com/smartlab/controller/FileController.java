package com.smartlab.controller;

import com.smartlab.config.OpenApiConfig;
import com.smartlab.dto.response.FileResponse;
import com.smartlab.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Files", description = "Permission-aware file upload, download, and soft deletion.")
public class FileController {
    private final FileService fileService;

    @GetMapping("/me/files")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List all active files uploaded by the current member")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public List<FileResponse> listOwn() {
        return fileService.listOwn(currentEmail());
    }

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('FILE_UPLOAD')")
    @Operation(summary = "Upload a file")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public FileResponse upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "PRIVATE") String accessScope,
            @RequestParam(required = false) @Size(max = 5000) String description,
            @RequestParam(required = false) Long projectId
    ) {
        if (projectId != null) {
            return fileService.uploadForProject(file, accessScope, description, currentEmail(), projectId);
        }
        return fileService.upload(file, accessScope, description, currentEmail());
    }

    @GetMapping("/files/{id}")
    @Operation(summary = "Download a file when its access scope permits")
    public ResponseEntity<byte[]> download(@PathVariable Long id, Authentication authentication) {
        FileService.DownloadedFile file = fileService.download(id, authentication);
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

    @DeleteMapping("/files/{id}")
    @PreAuthorize("hasAuthority('FILE_DELETE')")
    @Operation(summary = "Soft-delete an owned or administratively managed file")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        fileService.delete(id, currentEmail(), authentication);
        return ResponseEntity.noContent().build();
    }

    private String currentEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }
}
