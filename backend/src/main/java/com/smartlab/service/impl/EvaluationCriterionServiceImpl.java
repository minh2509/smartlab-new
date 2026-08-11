package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateEvaluationCriterionRequest;
import com.smartlab.dto.request.UpdateEvaluationCriterionRequest;
import com.smartlab.dto.response.EvaluationCriterionResponse;
import com.smartlab.entity.EvaluationCriterionEntity;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import com.smartlab.repo.EvaluationCriterionRepository;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.EvaluationCriterionService;
import com.smartlab.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EvaluationCriterionServiceImpl implements EvaluationCriterionService {

    private static final String ADMIN = "ADMIN";

    private final EvaluationCriterionRepository criterionRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    private UserEntity requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private ProjectEntity requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private void requireLeaderOrAdmin(UserEntity user, Long projectId) {
        Set<String> roles = permissionService.getRoleCodes(user);
        if (roles.contains(ADMIN)) return;
        boolean isLeader = projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                projectId, user.getId(), ProjectRole.LEADER, ProjectMemberStatus.ACTIVE
        );
        if (!isLeader) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only project leader or admin can manage evaluation criteria");
        }
    }

    private EvaluationCriterionResponse toResponse(EvaluationCriterionEntity entity) {
        UserEntity creator = entity.getCreatedBy();
        return EvaluationCriterionResponse.builder()
                .id(entity.getId())
                .projectId(entity.getProject().getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .maxScore(entity.getMaxScore())
                .displayOrder(entity.getDisplayOrder())
                .isActive(entity.getIsActive())
                .createdByUserId(creator != null ? creator.getId() : null)
                .createdByName(creator != null ? creator.getName() : null)
                .createdAt(entity.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvaluationCriterionResponse> listByProject(Long projectId, String currentEmail) {
        requireProject(projectId);
        return criterionRepository.findAllByProject_IdOrderByDisplayOrderAsc(projectId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public EvaluationCriterionResponse create(Long projectId, CreateEvaluationCriterionRequest request, String currentEmail) {
        UserEntity user = requireUser(currentEmail);
        ProjectEntity project = requireProject(projectId);
        requireLeaderOrAdmin(user, projectId);

        EvaluationCriterionEntity entity = EvaluationCriterionEntity.create(
                project,
                request.getName(),
                request.getDescription(),
                request.getMaxScore(),
                request.getDisplayOrder(),
                user
        );
        criterionRepository.save(entity);
        return toResponse(entity);
    }

    @Override
    @Transactional
    public EvaluationCriterionResponse update(Long projectId, Long criterionId, UpdateEvaluationCriterionRequest request, String currentEmail) {
        UserEntity user = requireUser(currentEmail);
        requireProject(projectId);
        requireLeaderOrAdmin(user, projectId);

        EvaluationCriterionEntity entity = criterionRepository.findByIdAndProject_Id(criterionId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Criterion not found"));

        entity.update(
                request.getName() != null ? request.getName() : entity.getName(),
                request.getDescription() != null ? request.getDescription() : entity.getDescription(),
                request.getMaxScore() != null ? request.getMaxScore() : entity.getMaxScore(),
                request.getDisplayOrder() != null ? request.getDisplayOrder() : entity.getDisplayOrder(),
                request.getIsActive() != null ? request.getIsActive() : entity.getIsActive()
        );
        return toResponse(entity);
    }
}
