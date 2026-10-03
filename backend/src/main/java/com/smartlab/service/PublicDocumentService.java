package com.smartlab.service;

import com.smartlab.dto.response.PublicDocumentCategoryResponse;
import com.smartlab.dto.response.PublicDocumentSummaryResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.PublicDocumentFileType;
import com.smartlab.enums.PublicDocumentSort;

import java.util.List;

public interface PublicDocumentService {
    PublicPageResponse<PublicDocumentSummaryResponse> list(
            String query,
            Long projectId,
            String category,
            PublicDocumentFileType fileType,
            Integer year,
            PublicDocumentSort sort,
            int page,
            int size
    );

    List<Integer> years();

    List<PublicDocumentCategoryResponse> categories(Integer year);
}
