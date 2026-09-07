package com.smartlab.controller;

import com.smartlab.dto.request.CreateLabArticleRequest;
import com.smartlab.dto.request.UpdateLabArticleRequest;
import com.smartlab.dto.response.AdminLabArticleResponse;
import com.smartlab.dto.response.PublicLabArticleDetailResponse;
import com.smartlab.dto.response.PublicLabArticleSummaryResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.service.LabArticleService;
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
public class LabArticleController {
    private final LabArticleService articleService;

    @GetMapping("/articles/latest")
    public List<PublicLabArticleSummaryResponse> latest(@RequestParam(defaultValue = "3") int limit) { return articleService.listLatest(limit); }
    @GetMapping("/articles")
    public PublicPageResponse<PublicLabArticleSummaryResponse> archive(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) { return articleService.listArchive(page, size); }
    @GetMapping("/articles/{slug}")
    public PublicLabArticleDetailResponse detail(@PathVariable String slug) { return articleService.getPublicBySlug(slug); }
    @GetMapping("/admin/articles") @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    public PublicPageResponse<AdminLabArticleResponse> listAdmin(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return articleService.listAdmin(page, size); }
    @PostMapping("/admin/articles") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    public AdminLabArticleResponse create(@Valid @RequestBody CreateLabArticleRequest request) { return articleService.create(request); }
    @PatchMapping("/admin/articles/{id}") @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    public AdminLabArticleResponse update(@PathVariable Long id, @Valid @RequestBody UpdateLabArticleRequest request) { return articleService.update(id, request); }
    @DeleteMapping("/admin/articles/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN') and hasAuthority('PROJECT_MANAGE')")
    public void delete(@PathVariable Long id) { articleService.delete(id); }
}
