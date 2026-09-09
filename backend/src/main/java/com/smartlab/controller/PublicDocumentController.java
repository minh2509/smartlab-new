package com.smartlab.controller;

import com.smartlab.dto.response.PublicDocumentSummaryResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.PublicDocumentFileType;
import com.smartlab.enums.PublicDocumentSort;
import com.smartlab.service.PublicDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PublicDocumentController {
    private final PublicDocumentService publicDocumentService;

    @GetMapping("/documents/public")
    public PublicPageResponse<PublicDocumentSummaryResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "ALL") PublicDocumentFileType fileType,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "LATEST") PublicDocumentSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return publicDocumentService.list(q, projectId, fileType, year, sort, page, size);
    }

    @GetMapping("/documents/public/years")
    public List<Integer> years() { return publicDocumentService.years(); }
}
