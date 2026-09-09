package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateResearchFieldRequest;
import com.smartlab.dto.request.UpdateResearchFieldRequest;
import com.smartlab.dto.response.ResearchFieldResponse;
import com.smartlab.entity.ResearchFieldEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.repo.ResearchFieldRepository;
import com.smartlab.repo.StoredFileRepository;
import com.smartlab.service.PostContentFileService;
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
    private final StoredFileRepository storedFileRepository;
    private final PostContentFileService postContentFileService;

    @Override
    @Transactional(readOnly = true)
    public List<ResearchFieldResponse> listActive() {
        return researchFieldRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ResearchFieldResponse getActiveByCode(String code) {
        return researchFieldRepository.findByCodeIgnoreCaseAndIsActiveTrue(code)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Research field not found"));
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
                .coverFile(request.getCoverFileId() == null ? null : resolveCoverFile(request.getCoverFileId()))
                .isActive(true)
                .build();
        return toResponse(researchFieldRepository.save(entity));
    }

    @Override
    @Transactional
    public ResearchFieldResponse update(Long id, UpdateResearchFieldRequest request) {
        ResearchFieldEntity entity = researchFieldRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Research field not found"));
        if (request.getCoverFileId() != null && Boolean.TRUE.equals(request.getRemoveCover())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "coverFileId and removeCover cannot both be set");
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            entity.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        if (request.getCoverFileId() != null) {
            entity.setCoverFile(resolveCoverFile(request.getCoverFileId()));
        } else if (Boolean.TRUE.equals(request.getRemoveCover())) {
            entity.setCoverFile(null);
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
                .coverFileId(entity.getCoverFile() == null ? null : entity.getCoverFile().getId())
                .isActive(entity.getIsActive())
                .build();
    }

    private StoredFileEntity resolveCoverFile(Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Research field cover file is unavailable");
        }
        PostContentFileService.FileMetadata metadata = postContentFileService.findActiveMetadata(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Research field cover file is unavailable"));
        if (!metadata.image()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Research field cover must use a supported image file");
        }
        if (!"PUBLIC".equals(metadata.accessScope())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Research field cover must use a PUBLIC file");
        }
        return storedFileRepository.getReferenceById(metadata.id());
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
