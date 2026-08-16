package com.smartlab.service.impl;

import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.ProjectResearchFieldEntity;
import com.smartlab.entity.ResearchFieldEntity;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.ProjectResearchFieldRepository;
import com.smartlab.repo.ResearchFieldRepository;
import com.smartlab.service.ProjectResearchFieldGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectResearchFieldGatewayImpl implements ProjectResearchFieldGateway {
    private final ProjectResearchFieldRepository projectResearchFieldRepository;
    private final ResearchFieldRepository researchFieldRepository;
    private final ProjectRepository projectRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ResearchFieldReference> getProjectFields(Long projectId) {
        return projectResearchFieldRepository.findAllWithFieldByProjectId(projectId).stream()
                .map(ProjectResearchFieldEntity::getResearchField)
                .map(field -> new ResearchFieldReference(
                        field.getId(), field.getCode(), field.getName(), field.getIsActive()
                ))
                .toList();
    }

    @Override
    @Transactional
    public void replaceProjectFields(Long projectId, Set<Long> fieldIds) {
        List<ProjectResearchFieldEntity> existing =
                projectResearchFieldRepository.findAllWithFieldByProjectId(projectId);
        Map<Long, ProjectResearchFieldEntity> existingByFieldId = new LinkedHashMap<>();
        existing.forEach(relation -> existingByFieldId.put(relation.getResearchField().getId(), relation));

        List<ResearchFieldEntity> requestedFields = fieldIds.isEmpty()
                ? List.of()
                : researchFieldRepository.findAllById(fieldIds);
        if (requestedFields.size() != fieldIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more research fields do not exist");
        }
        boolean containsNewInactiveField = requestedFields.stream().anyMatch(field ->
                !Boolean.TRUE.equals(field.getIsActive()) && !existingByFieldId.containsKey(field.getId())
        );
        if (containsNewInactiveField) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Inactive research fields cannot be newly assigned"
            );
        }

        List<ProjectResearchFieldEntity> removed = existing.stream()
                .filter(relation -> !fieldIds.contains(relation.getResearchField().getId()))
                .toList();
        if (!removed.isEmpty()) {
            projectResearchFieldRepository.deleteAll(removed);
            projectResearchFieldRepository.flush();
        }

        ProjectEntity project = projectRepository.getReferenceById(projectId);
        List<ProjectResearchFieldEntity> added = new ArrayList<>();
        for (ResearchFieldEntity field : requestedFields) {
            if (!existingByFieldId.containsKey(field.getId())) {
                added.add(ProjectResearchFieldEntity.create(project, field));
            }
        }
        if (!added.isEmpty()) {
            projectResearchFieldRepository.saveAllAndFlush(added);
        }
    }
}
