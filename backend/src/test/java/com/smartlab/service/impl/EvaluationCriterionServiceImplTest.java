package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateEvaluationCriterionRequest;
import com.smartlab.dto.request.UpdateEvaluationCriterionRequest;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import com.smartlab.repo.EvaluationCriterionRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationCriterionServiceImplTest {
    private static final long PROJECT_ID = 7L;
    private static final String EMAIL = "member@smartlab.test";

    @Mock private EvaluationCriterionRepository criterionRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private PermissionService permissionService;
    @Mock private ProjectEntity project;

    @InjectMocks private EvaluationCriterionServiceImpl service;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = user(1L, EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(permissionService.getRoleCodes(user)).thenReturn(Set.of());
    }

    @Test
    void listByProjectAllowsAnActiveMember() {
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, user.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(true);
        when(criterionRepository.findAllByProject_IdOrderByDisplayOrderAsc(PROJECT_ID)).thenReturn(List.of());

        assertThat(service.listByProject(PROJECT_ID, EMAIL)).isEmpty();

        verify(criterionRepository).findAllByProject_IdOrderByDisplayOrderAsc(PROJECT_ID);
    }

    @Test
    void listByProjectAllowsAnAdminWithoutMembership() {
        when(permissionService.getRoleCodes(user)).thenReturn(Set.of("ADMIN"));
        when(criterionRepository.findAllByProject_IdOrderByDisplayOrderAsc(PROJECT_ID)).thenReturn(List.of());

        assertThat(service.listByProject(PROJECT_ID, EMAIL)).isEmpty();

        verify(projectMemberRepository, never()).existsByProject_IdAndUser_IdAndStatus(any(), any(), any());
    }

    @Test
    void listByProjectRejectsAnAuthenticatedOutsider() {
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, user.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(false);

        assertStatus(() -> service.listByProject(PROJECT_ID, EMAIL), HttpStatus.FORBIDDEN);
    }

    @Test
    void listByProjectRejectsARemovedFormerMember() {
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, user.getId(), ProjectMemberStatus.ACTIVE)).thenReturn(false);

        assertStatus(() -> service.listByProject(PROJECT_ID, EMAIL), HttpStatus.FORBIDDEN);
        verify(projectMemberRepository).existsByProject_IdAndUser_IdAndStatus(
                PROJECT_ID, user.getId(), ProjectMemberStatus.ACTIVE);
    }

    @Test
    void createRemainsRestrictedToLeadersOrAdmins() {
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                PROJECT_ID, user.getId(), ProjectRole.LEADER, ProjectMemberStatus.ACTIVE)).thenReturn(false);

        assertStatus(() -> service.create(PROJECT_ID, createRequest(), EMAIL), HttpStatus.FORBIDDEN);

        verify(criterionRepository, never()).save(any());
    }

    @Test
    void updateRemainsRestrictedToLeadersOrAdmins() {
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                PROJECT_ID, user.getId(), ProjectRole.LEADER, ProjectMemberStatus.ACTIVE)).thenReturn(false);

        assertStatus(() -> service.update(PROJECT_ID, 3L, new UpdateEvaluationCriterionRequest(), EMAIL), HttpStatus.FORBIDDEN);

        verify(criterionRepository, never()).findByIdAndProject_Id(any(), any());
    }

    private static CreateEvaluationCriterionRequest createRequest() {
        CreateEvaluationCriterionRequest request = new CreateEvaluationCriterionRequest();
        request.setName("Quality");
        request.setMaxScore(BigDecimal.TEN);
        request.setDisplayOrder(1);
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
