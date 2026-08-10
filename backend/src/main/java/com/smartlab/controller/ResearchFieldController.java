package com.smartlab.controller;

import com.smartlab.dto.request.CreateResearchFieldRequest;
import com.smartlab.dto.request.UpdateResearchFieldRequest;
import com.smartlab.dto.response.ResearchFieldResponse;
import com.smartlab.service.ResearchFieldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ResearchFieldController {
    private final ResearchFieldService researchFieldService;

    @GetMapping("/research-fields")
    public List<ResearchFieldResponse> listActive() {
        return researchFieldService.listActive();
    }

    @GetMapping("/admin/research-fields")
    @PreAuthorize("hasAuthority('RESEARCH_FIELD_MANAGE')")
    public List<ResearchFieldResponse> listAll() {
        return researchFieldService.listAll();
    }

    @PostMapping("/admin/research-fields")
    @PreAuthorize("hasAuthority('RESEARCH_FIELD_MANAGE')")
    public ResearchFieldResponse create(@Valid @RequestBody CreateResearchFieldRequest request) {
        return researchFieldService.create(request);
    }

    @PatchMapping("/admin/research-fields/{id}")
    @PreAuthorize("hasAuthority('RESEARCH_FIELD_MANAGE')")
    public ResearchFieldResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateResearchFieldRequest request
    ) {
        return researchFieldService.update(id, request);
    }

    @DeleteMapping("/admin/research-fields/{id}")
    @PreAuthorize("hasAuthority('RESEARCH_FIELD_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        researchFieldService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
