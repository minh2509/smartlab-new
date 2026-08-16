package com.smartlab.service.impl;

import com.smartlab.dto.request.ReplaceProjectResearchFieldsRequest;
import com.smartlab.dto.response.ProjectResearchFieldResponse;
import com.smartlab.dto.response.ProjectResponse;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.ProjectResearchFieldRepository;
import com.smartlab.repo.ResearchFieldRepository;
import com.smartlab.service.ProjectAccessService;
import com.smartlab.service.ProjectResearchFieldGateway;
import com.smartlab.service.ProjectResearchFieldService;
import com.smartlab.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectResearchFieldServiceImpl implements ProjectResearchFieldService {
    private static final int FIELD_LIMIT = 100;

    private final ProjectRepository projectRepository;
    private final ResearchFieldRepository researchFieldRepository;
    private final ProjectResearchFieldRepository projectResearchFieldRepository;
    private final ProjectResearchFieldGateway projectResearchFieldGateway;
    private final ProjectAccessService projectAccessService;
    private final ProjectService projectService;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResearchFieldResponse> list(Long projectId, String currentEmail) {
        ProjectEntity project = requireProject(projectId);
        projectAccessService.requireRead(project, currentEmail);
        return toResponses(projectResearchFieldGateway.getProjectFields(projectId));
    }

    @Override
    @Transactional
    public List<ProjectResearchFieldResponse> replace(
            Long projectId,
            ReplaceProjectResearchFieldsRequest request,
            String currentEmail
    ) {
        ProjectEntity project = requireProjectForUpdate(projectId);
        projectAccessService.requireManage(project, currentEmail);
        Set<Long> fieldIds = normalizeFieldIds(request);
        projectResearchFieldGateway.replaceProjectFields(projectId, fieldIds);
        return toResponses(projectResearchFieldGateway.getProjectFields(projectId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> filterVisibleProjects(Long researchFieldId, String currentEmail) {
        if (researchFieldId == null || researchFieldId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Research field id must be positive");
        }
        researchFieldRepository.findById(researchFieldId)
                .filter(field -> Boolean.TRUE.equals(field.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Active research field not found: " + researchFieldId
                ));
        Set<Long> matchingIds = new LinkedHashSet<>(
                projectResearchFieldRepository.findActiveProjectIdsByFieldId(researchFieldId)
        );
        return projectService.list(currentEmail).stream()
                .filter(project -> matchingIds.contains(project.getId()))
                .toList();
    }

    private ProjectEntity requireProject(Long projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Project not found: " + projectId
                ));
    }

    private ProjectEntity requireProjectForUpdate(Long projectId) {
        return projectRepository.findActiveByIdForUpdate(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Project not found: " + projectId
                ));
    }

    private Set<Long> normalizeFieldIds(ReplaceProjectResearchFieldsRequest request) {
        if (request == null || request.getFieldIds() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Research field list is required");
        }
        if (request.getFieldIds().size() > FIELD_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most 100 research fields may be selected");
        }
        LinkedHashSet<Long> fieldIds = new LinkedHashSet<>();
        for (Long fieldId : request.getFieldIds()) {
            if (fieldId == null || fieldId <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Research field ids must be positive");
            }
            if (!fieldIds.add(fieldId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Research field ids must not contain duplicates");
            }
        }
        return fieldIds;
    }

    private List<ProjectResearchFieldResponse> toResponses(
            List<ProjectResearchFieldGateway.ResearchFieldReference> fields
    ) {
        return fields.stream()
                .map(field -> ProjectResearchFieldResponse.builder()
                        .id(field.id())
                        .code(field.code())
                        .name(field.name())
                        .isActive(field.isActive())
                        .build())
                .toList();
    }
}
