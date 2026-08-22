package com.smartlab.service;

import com.smartlab.dto.request.CreateResearchPublicationRequest;
import com.smartlab.dto.request.UpdateResearchPublicationRequest;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.dto.response.PublicationYearCountResponse;
import com.smartlab.dto.response.ResearchPublicationResponse;
import java.util.List;

public interface ResearchPublicationService {
    List<PublicationYearCountResponse> listPublicYears();
    PublicPageResponse<ResearchPublicationResponse> listPublic(Integer year, int page, int size);
    ResearchPublicationResponse create(CreateResearchPublicationRequest request);
    ResearchPublicationResponse update(Long id, UpdateResearchPublicationRequest request);
    void delete(Long id);
}
