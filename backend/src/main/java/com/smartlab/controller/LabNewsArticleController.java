package com.smartlab.controller;

import com.smartlab.dto.request.CreateLabNewsArticleRequest;
import com.smartlab.dto.request.UpdateLabNewsArticleRequest;
import com.smartlab.dto.response.LabNewsArticleResponse;
import com.smartlab.dto.response.AdminLabNewsArticleResponse;
import com.smartlab.dto.response.PublicLabNewsArticleResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.PublicNewsSort;
import com.smartlab.service.LabNewsArticleService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class LabNewsArticleController {
    private final LabNewsArticleService articleService;

    @GetMapping("/news")
    public List<PublicLabNewsArticleResponse> list(@RequestParam(defaultValue = "3") int limit) {
        return articleService.listPublic(limit).stream().map(this::toPublicResponse).toList();
    }

    @GetMapping("/news/archive")
    public PublicPageResponse<PublicLabNewsArticleResponse> listArchive(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "LATEST") PublicNewsSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        PublicPageResponse<LabNewsArticleResponse> result = articleService.listPublicArchive(q, source, year, sort, page, size);
        return new PublicPageResponse<>(result.items().stream().map(this::toPublicResponse).toList(), result.page(),
                result.size(), result.totalElements(), result.totalPages());
    }

    @GetMapping("/news/archive/sources")
    public List<String> sources() { return articleService.listPublicSources(); }

    @GetMapping("/news/archive/years")
    public List<Integer> years() { return articleService.listPublicYears(); }

    @GetMapping("/admin/news")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    public PublicPageResponse<AdminLabNewsArticleResponse> listAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PublicPageResponse<LabNewsArticleResponse> result = articleService.listAdmin(page, size);
        return new PublicPageResponse<>(result.items().stream().map(this::toAdminResponse).toList(), result.page(),
                result.size(), result.totalElements(), result.totalPages());
    }

    @PostMapping("/admin/news")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    public LabNewsArticleResponse create(@Valid @RequestBody CreateLabNewsArticleRequest request) {
        return articleService.create(request);
    }

    @PatchMapping("/admin/news/{id}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    public LabNewsArticleResponse update(@PathVariable Long id, @Valid @RequestBody UpdateLabNewsArticleRequest request) {
        return articleService.update(id, request);
    }

    @DeleteMapping("/admin/news/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    public void delete(@PathVariable Long id) {
        articleService.delete(id);
    }

    private PublicLabNewsArticleResponse toPublicResponse(LabNewsArticleResponse response) {
        return new PublicLabNewsArticleResponse(response.id(), response.title(), response.excerpt(), response.sourceName(),
                response.sourceUrl(), response.publishedAt());
    }

    private AdminLabNewsArticleResponse toAdminResponse(LabNewsArticleResponse response) {
        return new AdminLabNewsArticleResponse(response.id(), response.title(), response.excerpt(), response.sourceName(),
                response.sourceUrl(), response.publishedAt(), response.isPublic(), response.createdAt(), response.updatedAt());
    }
}
