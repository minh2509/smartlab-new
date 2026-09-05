package com.smartlab.service;

import com.smartlab.dto.request.CreateLabArticleRequest;
import com.smartlab.dto.request.UpdateLabArticleRequest;
import com.smartlab.dto.response.AdminLabArticleResponse;
import com.smartlab.dto.response.PublicLabArticleDetailResponse;
import com.smartlab.dto.response.PublicLabArticleSummaryResponse;
import com.smartlab.dto.response.PublicPageResponse;

import java.util.List;

public interface LabArticleService {
    List<PublicLabArticleSummaryResponse> listLatest(int limit);
    PublicPageResponse<PublicLabArticleSummaryResponse> listArchive(int page, int size);
    PublicLabArticleDetailResponse getPublicBySlug(String slug);
    PublicPageResponse<AdminLabArticleResponse> listAdmin(int page, int size);
    AdminLabArticleResponse create(CreateLabArticleRequest request);
    AdminLabArticleResponse update(Long id, UpdateLabArticleRequest request);
    void delete(Long id);
}
