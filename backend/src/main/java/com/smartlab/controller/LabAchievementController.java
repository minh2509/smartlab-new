package com.smartlab.controller;

import com.smartlab.dto.request.CreateLabAchievementRequest;
import com.smartlab.dto.request.UpdateLabAchievementRequest;
import com.smartlab.dto.response.AchievementYearCountResponse;
import com.smartlab.dto.response.AdminLabAchievementResponse;
import com.smartlab.dto.response.LabAchievementResponse;
import com.smartlab.dto.response.LabAchievementFileResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.AchievementType;
import com.smartlab.service.LabAchievementService;
import com.smartlab.validation.AchievementYearPolicy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.nio.charset.StandardCharsets;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;

@RestController
@RequiredArgsConstructor
public class LabAchievementController {
    private final LabAchievementService achievementService;

    @GetMapping("/achievements")
    public PublicPageResponse<LabAchievementResponse> list(
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) { return achievementService.listPublic(year, page, size); }

    @GetMapping("/achievements/years")
    public List<AchievementYearCountResponse> years() { return achievementService.listPublicYears(); }

    @GetMapping("/admin/achievements")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    public PublicPageResponse<AdminLabAchievementResponse> listAdmin(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) AchievementType type,
            @RequestParam(required = false) Boolean isPublic,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String normalizedQuery = normalizeQuery(q);
        validateAdminParameters(year, normalizedQuery, page, size);
        return achievementService.listAdmin(year, type, isPublic, normalizedQuery, page, size);
    }

    @PostMapping("/admin/achievements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    public AdminLabAchievementResponse create(@Valid @RequestBody CreateLabAchievementRequest request) { return achievementService.create(request); }

    @PatchMapping("/admin/achievements/{id}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    public AdminLabAchievementResponse update(@PathVariable Long id, @Valid @RequestBody UpdateLabAchievementRequest request) { return achievementService.update(id, request); }

    @DeleteMapping("/admin/achievements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    public void delete(@PathVariable Long id) { achievementService.delete(id); }

    @GetMapping("/admin/achievements/{achievementId}/files")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    public List<LabAchievementFileResponse> listFiles(@PathVariable Long achievementId) { return achievementService.listFiles(achievementId); }

    @PostMapping(value = "/admin/achievements/{achievementId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE') and hasAuthority('FILE_UPLOAD')")
    public LabAchievementFileResponse uploadFile(@PathVariable Long achievementId, @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 500) String label, Authentication authentication) {
        return achievementService.uploadFile(achievementId, file, label, authentication.getName());
    }

    @DeleteMapping("/admin/achievements/{achievementId}/files/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    public void detachFile(@PathVariable Long achievementId, @PathVariable Long attachmentId) {
        achievementService.detachFile(achievementId, attachmentId);
    }

    @GetMapping("/achievements/{achievementId}/files/{attachmentId}")
    public ResponseEntity<byte[]> downloadPublicFile(@PathVariable Long achievementId, @PathVariable Long attachmentId) {
        LabAchievementService.FileDownload file = achievementService.downloadPublicFile(achievementId, attachmentId);
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(file.mimeType()); }
        catch (IllegalArgumentException exception) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.originalName(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(file.content());
    }

    private void validateAdminParameters(Integer year, String q, int page, int size) {
        if (page < 0) throw badRequest("Page must not be negative");
        if (size < 1 || size > 100) throw badRequest("Size must be between 1 and 100");
        if (year != null && !AchievementYearPolicy.isValid(year)) throw badRequest(AchievementYearPolicy.ERROR_MESSAGE);
        if (q != null && q.length() > 200) throw badRequest("Query must be at most 200 characters");
    }

    private String normalizeQuery(String q) {
        if (q == null) return null;
        String normalized = q.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
