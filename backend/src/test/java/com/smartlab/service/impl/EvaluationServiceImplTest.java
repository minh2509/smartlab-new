package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateEvaluationRequest;
import com.smartlab.dto.request.UpdateEvaluationRequest;
import com.smartlab.entity.EvaluationCriterionEntity;
import com.smartlab.entity.EvaluationEntity;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import com.smartlab.repo.EvaluationCriterionRepository;
import com.smartlab.repo.EvaluationRepository;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceImplTest {
    private static final long PROJECT_ID = 7L;
    private static final String EMAIL = "leader@smartlab.test";

    @Mock private EvaluationRepository evaluationRepository;
    @Mock private EvaluationCriterionRepository criterionRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private PermissionService permissionService;
    @Mock private ProjectEntity project;

    @InjectMocks private EvaluationServiceImpl service;

    private UserEntity evaluator;
    private UserEntity evaluatedUser;

    @BeforeEach
    void setUp() {
        evaluator = user(1L, EMAIL);
        evaluatedUser = user(2L, "member@smartlab.test");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(evaluator));
        when(permissionService.getRoleCodes(evaluator)).thenReturn(Set.of("ADMIN"));
    }

    @Test
    void createRejectsAnEvaluatedUserWhoIsNotAnActiveProjectMember() {
        stubCreatePrerequisites();
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, evaluatedUser.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(false);

        assertStatus(() -> service.create(PROJECT_ID, createRequest(3L, BigDecimal.ONE), EMAIL), HttpStatus.BAD_REQUEST);

        verify(userRepository).findByUserId(evaluatedUser.getUserId());
        verify(userRepository, never()).findById(any());
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void createRejectsACriterionFromAnotherProject() {
        stubCreatePrerequisites();
        allowEvaluatedMember();
        when(criterionRepository.findByIdAndProject_Id(3L, PROJECT_ID)).thenReturn(Optional.empty());

        assertStatus(() -> service.create(PROJECT_ID, createRequest(3L, BigDecimal.ONE), EMAIL), HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRejectsAScoreGreaterThanTheCriterionMaximum() {
        stubCreatePrerequisites();
        allowEvaluatedMember();
        EvaluationCriterionEntity criterion = EvaluationCriterionEntity.create(
                project, "Quality", null, BigDecimal.TEN, 1, evaluator);
        when(criterionRepository.findByIdAndProject_Id(3L, PROJECT_ID)).thenReturn(Optional.of(criterion));

        assertStatus(() -> service.create(PROJECT_ID, createRequest(3L, new BigDecimal("10.01")), EMAIL), HttpStatus.BAD_REQUEST);
    }

    @Test
    void nonLeaderNonAdminCannotCreateAnEvaluation() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(permissionService.getRoleCodes(evaluator)).thenReturn(Set.of());
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                PROJECT_ID, evaluator.getId(), ProjectRole.LEADER, ProjectMemberStatus.ACTIVE)).thenReturn(false);

        assertStatus(() -> service.create(PROJECT_ID, createRequest(3L, BigDecimal.ONE), EMAIL), HttpStatus.FORBIDDEN);

        verify(userRepository, never()).findByUserId(any());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void nonLeaderNonAdminCannotUpdateAnEvaluation() {
        when(permissionService.getRoleCodes(evaluator)).thenReturn(Set.of());
        EvaluationEntity evaluation = EvaluationEntity.create(project, evaluator, evaluatedUser, null);
        when(project.getId()).thenReturn(PROJECT_ID);
        when(evaluationRepository.findByIdWithScores(4L)).thenReturn(Optional.of(evaluation));
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                PROJECT_ID, evaluator.getId(), ProjectRole.LEADER, ProjectMemberStatus.ACTIVE)).thenReturn(false);

        assertStatus(() -> service.update(4L, new UpdateEvaluationRequest(), EMAIL), HttpStatus.FORBIDDEN);

        verify(criterionRepository, never()).findByIdAndProject_Id(any(), any());
    }

    private void allowEvaluatedMember() {
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, evaluatedUser.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(true);
    }

    private void stubCreatePrerequisites() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(userRepository.findByUserId(eq(evaluatedUser.getUserId()))).thenReturn(Optional.of(evaluatedUser));
    }

    private static CreateEvaluationRequest createRequest(long criterionId, BigDecimal score) {
        CreateEvaluationRequest.ScoreEntry entry = new CreateEvaluationRequest.ScoreEntry();
        entry.setCriterionId(criterionId);
        entry.setScore(score);
        CreateEvaluationRequest request = new CreateEvaluationRequest();
        request.setEvaluatedUserId("user-2");
        request.setScores(List.of(entry));
        return request;
    }

    private static UserEntity user(long id, String email) {
        return UserEntity.builder().id(id).userId("user-" + id).name("User " + id)
                .email(email).password("encoded").isActive(true).isAccountVerified(true).build();
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, HttpStatus status) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(status));
    }
}
