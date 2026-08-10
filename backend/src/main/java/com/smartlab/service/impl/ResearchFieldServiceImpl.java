package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateResearchFieldRequest;
import com.smartlab.dto.request.UpdateResearchFieldRequest;
import com.smartlab.dto.response.ResearchFieldResponse;
import com.smartlab.entity.ResearchFieldEntity;
import com.smartlab.repo.ResearchFieldRepository;
import com.smartlab.service.ResearchFieldService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ResearchFieldServiceImpl implements ResearchFieldService {
    private final ResearchFieldRepository researchFieldRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ResearchFieldResponse> listActive() {
        return researchFieldRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResearchFieldResponse> listAll() {
        return researchFieldRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ResearchFieldResponse create(CreateResearchFieldRequest request) {
        String code = normalizeCode(request.getCode());
        if (researchFieldRepository.existsByCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Research field code already exists");
        }
        ResearchFieldEntity entity = ResearchFieldEntity.builder()
                .code(code)
                .name(request.getName().trim())
                .description(request.getDescription())
                .isActive(true)
                .build();
        return toResponse(researchFieldRepository.save(entity));
    }

    @Override
    @Transactional
    public ResearchFieldResponse update(Long id, UpdateResearchFieldRequest request) {
        ResearchFieldEntity entity = researchFieldRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Research field not found"));
        if (request.getName() != null && !request.getName().isBlank()) {
            entity.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        return toResponse(researchFieldRepository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ResearchFieldEntity entity = researchFieldRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Research field not found"));
        entity.setIsActive(false);
        researchFieldRepository.save(entity);
    }

    private ResearchFieldResponse toResponse(ResearchFieldEntity entity) {
        return ResearchFieldResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .build();
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
