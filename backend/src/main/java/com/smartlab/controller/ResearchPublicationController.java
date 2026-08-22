package com.smartlab.controller;

import com.smartlab.dto.request.CreateResearchPublicationRequest;
import com.smartlab.dto.request.UpdateResearchPublicationRequest;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.dto.response.PublicationYearCountResponse;
import com.smartlab.dto.response.ResearchPublicationResponse;
import com.smartlab.service.ResearchPublicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController @RequiredArgsConstructor
public class ResearchPublicationController {
    private final ResearchPublicationService publicationService;
    @GetMapping("/publications/years") public List<PublicationYearCountResponse> years() { return publicationService.listPublicYears(); }
    @GetMapping("/publications") public PublicPageResponse<ResearchPublicationResponse> list(@RequestParam(required = false) Integer year, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "8") int size) { return publicationService.listPublic(year, page, size); }
    @PostMapping("/admin/publications") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')") public ResearchPublicationResponse create(@Valid @RequestBody CreateResearchPublicationRequest request) { return publicationService.create(request); }
    @PatchMapping("/admin/publications/{id}") @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')") public ResearchPublicationResponse update(@PathVariable Long id, @Valid @RequestBody UpdateResearchPublicationRequest request) { return publicationService.update(id, request); }
    @DeleteMapping("/admin/publications/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')") public void delete(@PathVariable Long id) { publicationService.delete(id); }
}
