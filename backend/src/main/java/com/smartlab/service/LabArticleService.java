package com.smartlab.service;

import com.smartlab.dto.request.CreateLabArticleRequest;
import com.smartlab.dto.request.UpdateLabArticleRequest;
import com.smartlab.dto.response.AdminLabArticleResponse;
import com.smartlab.dto.response.PublicLabArticleDetailResponse;
import com.smartlab.dto.response.PublicLabArticleSummaryResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.PublicArticleSort;

import java.util.List;

public interface LabArticleService {
    List<PublicLabArticleSummaryResponse> listLatest(int limit);
    PublicPageResponse<PublicLabArticleSummaryResponse> listArchive(String q, Integer year, PublicArticleSort sort, int page, int size);
    List<Integer> listPublishedYears();
    PublicLabArticleDetailResponse getPublicBySlug(String slug);
    PublicPageResponse<AdminLabArticleResponse> listAdmin(int page, int size);
    AdminLabArticleResponse create(CreateLabArticleRequest request);
    AdminLabArticleResponse update(Long id, UpdateLabArticleRequest request);
    void delete(Long id);
}
