package com.smartlab.controller;

import com.smartlab.dto.request.CreateGalleryItemRequest;
import com.smartlab.dto.request.UpdateGalleryItemRequest;
import com.smartlab.dto.response.AdminGalleryItemResponse;
import com.smartlab.dto.response.PublicGalleryItemResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.GalleryCategory;
import com.smartlab.enums.GalleryItemStatus;
import com.smartlab.enums.PublicGallerySort;
import com.smartlab.service.GalleryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class GalleryController {
    private final GalleryService galleryService;

    @GetMapping("/gallery/public")
    @PreAuthorize("permitAll()")
    public PublicPageResponse<PublicGalleryItemResponse> listPublic(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) GalleryCategory category,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "LATEST") PublicGallerySort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size
    ) {
        return galleryService.listPublic(q, category, projectId, year, sort, page, size);
    }

    @GetMapping("/gallery/public/years")
    @PreAuthorize("permitAll()")
    public List<Integer> publicYears() { return galleryService.listPublicYears(); }

    @GetMapping("/admin/gallery")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('GALLERY_MANAGE')")
    public PublicPageResponse<AdminGalleryItemResponse> listAdmin(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) GalleryItemStatus status,
            @RequestParam(required = false) GalleryCategory category,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "LATEST") PublicGallerySort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size
    ) {
        return galleryService.listAdmin(q, status, category, projectId, year, sort, page, size);
    }

    @PostMapping(value = "/admin/gallery", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('GALLERY_MANAGE') and hasAuthority('FILE_UPLOAD')")
    public AdminGalleryItemResponse create(
            @Valid @ModelAttribute CreateGalleryItemRequest request,
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        return galleryService.create(request, file, authentication.getName());
    }

    @PatchMapping("/admin/gallery/{id}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('GALLERY_MANAGE')")
    public AdminGalleryItemResponse update(@PathVariable Long id, @Valid @RequestBody UpdateGalleryItemRequest request) {
        return galleryService.update(id, request);
    }

    @PostMapping("/admin/gallery/{id}/publish")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('GALLERY_MANAGE')")
    public AdminGalleryItemResponse publish(@PathVariable Long id) { return galleryService.publish(id); }

    @PostMapping("/admin/gallery/{id}/unpublish")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('GALLERY_MANAGE')")
    public AdminGalleryItemResponse unpublish(@PathVariable Long id) { return galleryService.unpublish(id); }

    @DeleteMapping("/admin/gallery/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('GALLERY_MANAGE')")
    public void delete(@PathVariable Long id) { galleryService.delete(id); }
}
