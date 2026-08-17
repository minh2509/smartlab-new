package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateEvaluationRequest;
import com.smartlab.dto.request.UpdateEvaluationRequest;
import com.smartlab.dto.response.EvaluationResponse;
import com.smartlab.entity.EvaluationCriterionEntity;
import com.smartlab.entity.EvaluationEntity;
import com.smartlab.entity.EvaluationScoreEntity;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import com.smartlab.repo.EvaluationCriterionRepository;
import com.smartlab.repo.EvaluationRepository;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.EvaluationService;
import com.smartlab.service.NotificationRelated;
import com.smartlab.service.NotificationService;
import com.smartlab.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

    private static final String ADMIN = "ADMIN";

    private final EvaluationRepository evaluationRepository;
    private final EvaluationCriterionRepository criterionRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final NotificationService notificationService;

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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only project leader or admin can create evaluations");
        }
    }

    private EvaluationResponse toResponse(EvaluationEntity entity) {
        List<EvaluationResponse.ScoreItem> scoreItems = entity.getScores().stream()
                .map(s -> EvaluationResponse.ScoreItem.builder()
                        .criterionId(s.getCriterion().getId())
                        .criterionName(s.getCriterion().getName())
                        .maxScore(s.getCriterion().getMaxScore())
                        .score(s.getScore())
                        .note(s.getNote())
                        .build())
                .toList();

        return EvaluationResponse.builder()
                .id(entity.getId())
                .projectId(entity.getProject().getId())
                .projectName(entity.getProject().getName())
                .evaluatorUserId(entity.getEvaluator().getId())
                .evaluatorName(entity.getEvaluator().getName())
                .evaluatedUserId(entity.getEvaluatedUser().getId())
                .evaluatedUserName(entity.getEvaluatedUser().getName())
                .note(entity.getNote())
                .scores(scoreItems)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public EvaluationResponse create(Long projectId, CreateEvaluationRequest request, String currentEmail) {
        UserEntity evaluator = requireUser(currentEmail);
        ProjectEntity project = requireProject(projectId);
        requireLeaderOrAdmin(evaluator, projectId);

        UserEntity evaluatedUser = userRepository.findByUserId(request.getEvaluatedUserId().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluated user not found"));

        boolean evaluatedIsMember = projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                projectId, evaluatedUser.getId(), ProjectMemberStatus.ACTIVE
        );
        if (!evaluatedIsMember) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evaluated user is not an active member of this project");
        }

        EvaluationEntity evaluation = EvaluationEntity.create(project, evaluator, evaluatedUser, request.getNote());
        evaluationRepository.save(evaluation);

        List<EvaluationScoreEntity> scores = buildScores(evaluation, request.getScores(), projectId);
        evaluation.replaceScores(scores);

        notificationService.notify(
                evaluatedUser.getId(),
                "EVALUATION_CREATED",
                "Bạn nhận được đánh giá mới từ Leader trong dự án " + project.getName(),
                new NotificationRelated(evaluator.getId(), "EVALUATION", evaluation.getId(), "/my-evaluations"),
                Instant.now()
        );

        return toResponse(evaluation);
    }

    @Override
    @Transactional
    public EvaluationResponse update(Long evaluationId, UpdateEvaluationRequest request, String currentEmail) {
        UserEntity user = requireUser(currentEmail);
        EvaluationEntity evaluation = evaluationRepository.findByIdWithScores(evaluationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluation not found"));

        requireLeaderOrAdmin(user, evaluation.getProject().getId());

        boolean changed = false;
        if (request.getNote() != null) {
            evaluation.updateNote(request.getNote());
            changed = true;
        }

        if (request.getScores() != null && !request.getScores().isEmpty()) {
            List<EvaluationScoreEntity> scores = buildScoresFromUpdate(
                    evaluation, request.getScores(), evaluation.getProject().getId()
            );
            evaluation.replaceScores(scores);
            changed = true;
        }

        if (changed) {
            notificationService.notify(
                    evaluation.getEvaluatedUser().getId(),
                    "EVALUATION_UPDATED",
                    "Đánh giá của bạn trong dự án " + evaluation.getProject().getName() + " đã được cập nhật",
                    new NotificationRelated(user.getId(), "EVALUATION", evaluation.getId(), "/my-evaluations"),
                    Instant.now()
            );
        }

        return toResponse(evaluation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvaluationResponse> getMyEvaluations(String currentEmail) {
        UserEntity user = requireUser(currentEmail);
        return evaluationRepository.findAllByEvaluatedUserId(user.getId())
                .stream().map(this::toResponse).toList();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<EvaluationScoreEntity> buildScores(
            EvaluationEntity evaluation,
            List<CreateEvaluationRequest.ScoreEntry> entries,
            Long projectId
    ) {
        List<EvaluationScoreEntity> scores = new ArrayList<>();
        for (CreateEvaluationRequest.ScoreEntry entry : entries) {
            EvaluationCriterionEntity criterion = criterionRepository.findByIdAndProject_Id(entry.getCriterionId(), projectId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Criterion " + entry.getCriterionId() + " not found in this project"));
            if (!Boolean.TRUE.equals(criterion.getIsActive())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Criterion " + criterion.getName() + " is inactive");
            }
            if (entry.getScore().compareTo(criterion.getMaxScore()) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Score for criterion '" + criterion.getName() + "' exceeds max score of " + criterion.getMaxScore());
            }
            scores.add(EvaluationScoreEntity.of(evaluation, criterion, entry.getScore(), entry.getNote()));
        }
        return scores;
    }

    private List<EvaluationScoreEntity> buildScoresFromUpdate(
            EvaluationEntity evaluation,
            List<UpdateEvaluationRequest.ScoreEntry> entries,
            Long projectId
    ) {
        List<EvaluationScoreEntity> scores = new ArrayList<>();
        for (UpdateEvaluationRequest.ScoreEntry entry : entries) {
            EvaluationCriterionEntity criterion = criterionRepository.findByIdAndProject_Id(entry.getCriterionId(), projectId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Criterion " + entry.getCriterionId() + " not found in this project"));
            if (entry.getScore().compareTo(criterion.getMaxScore()) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Score for criterion '" + criterion.getName() + "' exceeds max score of " + criterion.getMaxScore());
            }
            scores.add(EvaluationScoreEntity.of(evaluation, criterion, entry.getScore(), entry.getNote()));
        }
        return scores;
    }
}
