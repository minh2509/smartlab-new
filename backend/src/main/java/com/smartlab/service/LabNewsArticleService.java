package com.smartlab.service;

import com.smartlab.dto.request.CreateLabNewsArticleRequest;
import com.smartlab.dto.request.UpdateLabNewsArticleRequest;
import com.smartlab.dto.response.LabNewsArticleResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.PublicNewsSort;

import java.util.List;

public interface LabNewsArticleService {
    List<LabNewsArticleResponse> listPublic(int limit);
    PublicPageResponse<LabNewsArticleResponse> listPublicArchive(int page, int size);
    PublicPageResponse<LabNewsArticleResponse> listPublicArchive(String query, String source, Integer year,
                                                                  PublicNewsSort sort, int page, int size);
    List<String> listPublicSources();
    List<Integer> listPublicYears();
    PublicPageResponse<LabNewsArticleResponse> listAdmin(int page, int size);
    LabNewsArticleResponse create(CreateLabNewsArticleRequest request);
    LabNewsArticleResponse update(Long id, UpdateLabNewsArticleRequest request);
    void delete(Long id);
}
