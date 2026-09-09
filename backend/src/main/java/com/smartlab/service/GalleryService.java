package com.smartlab.service;

import com.smartlab.dto.request.CreateGalleryItemRequest;
import com.smartlab.dto.request.UpdateGalleryItemRequest;
import com.smartlab.dto.response.AdminGalleryItemResponse;
import com.smartlab.dto.response.PublicGalleryItemResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.GalleryCategory;
import com.smartlab.enums.GalleryItemStatus;
import com.smartlab.enums.PublicGallerySort;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface GalleryService {
    PublicPageResponse<PublicGalleryItemResponse> listPublic(String query, GalleryCategory category, Long projectId,
                                                              Integer year, PublicGallerySort sort, int page, int size);

    List<Integer> listPublicYears();

    PublicPageResponse<AdminGalleryItemResponse> listAdmin(String query, GalleryItemStatus status,
                                                            GalleryCategory category, Long projectId, Integer year,
                                                            PublicGallerySort sort, int page, int size);

    AdminGalleryItemResponse create(CreateGalleryItemRequest request, MultipartFile file, String email);

    AdminGalleryItemResponse update(Long id, UpdateGalleryItemRequest request);

    AdminGalleryItemResponse publish(Long id);

    AdminGalleryItemResponse unpublish(Long id);

    void delete(Long id);
}
