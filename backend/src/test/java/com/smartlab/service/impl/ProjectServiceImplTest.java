package com.smartlab.service.impl;

import com.smartlab.dto.request.ChangeProjectLeaderRequest;
import com.smartlab.dto.request.ChangeProjectLeadersRequest;
import com.smartlab.dto.request.CreateProjectRequest;
import com.smartlab.dto.request.UpdateProjectRequest;
import com.smartlab.dto.request.UpdateProjectLeadershipRequest;
import com.smartlab.dto.response.LeaderCandidateResponse;
import com.smartlab.dto.response.ProjectLeaderResponse;
import com.smartlab.dto.response.ProjectResponse;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.ProjectMemberEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
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
class ProjectServiceImplTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private ProjectServiceImpl projectService;

    @Test
    void createsProjectWithPrimaryAndAdditionalLeaders() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        UserEntity primary = user(2L, "primary-user", "primary@smartlab.test", true);
        UserEntity coLeader = user(3L, "co-user", "co@smartlab.test", true);
        stubAdmin(admin);
        when(projectRepository.existsByCodeIgnoreCase("SL-AI")).thenReturn(false);
        when(userRepository.findAllByUserIdInForUpdate(Set.of("primary-user", "co-user")))
                .thenReturn(List.of(primary, coLeader));
        when(projectRepository.saveAndFlush(any(ProjectEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateProjectRequest request = minimalCreateRequest(" sl-ai ", " Smart Lab AI ", "primary-user");
        request.setAdditionalLeaderUserIds(List.of("co-user", "primary-user"));

        ProjectResponse response = projectService.create(request, admin.getEmail());

        assertThat(response.getCode()).isEqualTo("SL-AI");
        assertThat(response.getPrimaryLeader().getUserId()).isEqualTo("primary-user");
        assertThat(response.getLeaders())
                .extracting(ProjectLeaderResponse::getUserId)
                .containsExactly("primary-user", "co-user");
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Iterable> memberships = ArgumentCaptor.forClass(Iterable.class);
        verify(projectMemberRepository).saveAllAndFlush(memberships.capture());
        assertThat(memberships.getValue()).asList().hasSize(2);
    }

    @Test
    void searchesAtMostTwentyAssignableLeaderCandidatesWithMinimalFields() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        UserEntity candidate = user(2L, "leader-user", "leader@smartlab.test", true);
        stubAdmin(admin);
        when(userRepository.findAssignableLeaderCandidates("nguyen", PageRequest.of(0, 20)))
                .thenReturn(List.of(candidate));

        List<LeaderCandidateResponse> response = projectService.findLeaderCandidates(
                "  nguyen  ",
                admin.getEmail()
        );

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.getUserId()).isEqualTo("leader-user");
            assertThat(item.getName()).isEqualTo("leader-user");
            assertThat(item.getEmail()).isEqualTo("leader@smartlab.test");
        });
        verify(userRepository).findAssignableLeaderCandidates("nguyen", PageRequest.of(0, 20));
    }

    @Test
    void rejectsOversizedLeaderCandidateQueryBeforeRepositorySearch() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        stubAdmin(admin);

        assertStatus(
                () -> projectService.findLeaderCandidates("q".repeat(101), admin.getEmail()),
                HttpStatus.BAD_REQUEST
        );

        verify(userRepository, never()).findAssignableLeaderCandidates(any(), any());
    }

    @Test
    void createsProjectWithoutAnyLeader() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        stubAdmin(admin);
        when(projectRepository.existsByCodeIgnoreCase("SL-OPEN")).thenReturn(false);
        when(projectRepository.saveAndFlush(any(ProjectEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateProjectRequest request = minimalCreateRequest("SL-OPEN", "Unassigned project", null);

        ProjectResponse response = projectService.create(request, admin.getEmail());

        assertThat(response.getPrimaryLeader()).isNull();
        assertThat(response.getLeaders()).isEmpty();
        verify(userRepository, never()).findAllByUserIdInForUpdate(any());
        verify(projectMemberRepository).saveAllAndFlush(List.of());
    }

    @Test
    void rejectsMoreThanOneHundredDistinctLeadersOnCreateBeforeLockingAccounts() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        stubAdmin(admin);
        when(projectRepository.existsByCodeIgnoreCase("SL-LARGE")).thenReturn(false);
        CreateProjectRequest request = minimalCreateRequest("SL-LARGE", "Large leadership", "primary-user");
        request.setAdditionalLeaderUserIds(java.util.stream.IntStream.range(0, 100)
                .mapToObj(index -> "additional-" + index)
                .toList());

        assertStatus(() -> projectService.create(request, admin.getEmail()), HttpStatus.BAD_REQUEST);

        verify(userRepository, never()).findAllByUserIdInForUpdate(any());
        verify(projectRepository, never()).saveAndFlush(any(ProjectEntity.class));
    }

    @Test
    void onlyAdminCanCreateProjectWhenControllerSecurityIsBypassed() {
        UserEntity member = user(3L, "member-user", "member@smartlab.test", true);
        when(userRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
        when(permissionService.getRoleCodes(member)).thenReturn(Set.of("MEMBER"));

        assertStatus(
                () -> projectService.create(
                        minimalCreateRequest("SL-AI", "Smart Lab AI", "leader-user"),
                        member.getEmail()
                ),
                HttpStatus.FORBIDDEN
        );

        verify(projectRepository, never()).saveAndFlush(any(ProjectEntity.class));
    }

    @Test
    void rejectsInactiveAdditionalLeader() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        UserEntity primary = user(2L, "primary-user", "primary@smartlab.test", true);
        UserEntity inactive = user(3L, "inactive-user", "inactive@smartlab.test", false);
        stubAdmin(admin);
        when(projectRepository.existsByCodeIgnoreCase("SL-AI")).thenReturn(false);
        when(userRepository.findAllByUserIdInForUpdate(Set.of("primary-user", "inactive-user")))
                .thenReturn(List.of(primary, inactive));
        CreateProjectRequest request = minimalCreateRequest("SL-AI", "Smart Lab AI", "primary-user");
        request.setAdditionalLeaderUserIds(List.of("inactive-user"));

        assertStatus(() -> projectService.create(request, admin.getEmail()), HttpStatus.BAD_REQUEST);

        verify(projectRepository, never()).saveAndFlush(any(ProjectEntity.class));
    }

    @Test
    void activeProjectLeaderCanUpdateWhileGlobalRoleIsMember() {
        UserEntity primary = user(2L, "primary-user", "primary@smartlab.test", true);
        UserEntity coLeader = user(3L, "co-user", "co@smartlab.test", true);
        ProjectEntity project = project(7L, primary, false, ProjectStatus.PROPOSED);
        when(userRepository.findByEmail(coLeader.getEmail())).thenReturn(Optional.of(coLeader));
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(permissionService.getRoleCodes(coLeader)).thenReturn(Set.of("MEMBER"));
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                7L,
                3L,
                ProjectRole.LEADER,
                ProjectMemberStatus.ACTIVE
        )).thenReturn(true);
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setName(" Updated name ");
        ProjectResponse response = projectService.update(7L, request, coLeader.getEmail());

        assertThat(response.getName()).isEqualTo("Updated name");
        verify(projectRepository).saveAndFlush(project);
    }

    @Test
    void projectManageDoesNotAllowLeaderToUpdateUnassignedProject() {
        UserEntity primary = user(2L, "primary-user", "primary@smartlab.test", true);
        UserEntity outsider = user(3L, "other-leader", "other@smartlab.test", true);
        ProjectEntity project = project(7L, primary, false, ProjectStatus.PROPOSED);
        when(userRepository.findByEmail(outsider.getEmail())).thenReturn(Optional.of(outsider));
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(permissionService.getRoleCodes(outsider)).thenReturn(Set.of("LEADER"));

        assertStatus(
                () -> projectService.update(7L, new UpdateProjectRequest(), outsider.getEmail()),
                HttpStatus.FORBIDDEN
        );

        verify(projectRepository, never()).saveAndFlush(any(ProjectEntity.class));
    }

    @Test
    void changingPrimaryKeepsOldPrimaryAndOtherCoLeaders() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        UserEntity oldPrimary = user(2L, "old-primary", "old@smartlab.test", true);
        UserEntity newPrimary = user(3L, "new-primary", "new@smartlab.test", true);
        UserEntity coLeader = user(4L, "co-leader", "co@smartlab.test", true);
        ProjectEntity project = project(7L, oldPrimary, false, ProjectStatus.IN_PROGRESS);
        ProjectMemberEntity oldMembership = ProjectMemberEntity.createLeader(project, oldPrimary);
        ProjectMemberEntity coMembership = ProjectMemberEntity.createLeader(project, coLeader);
        stubAdmin(admin);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(userRepository.findAllByUserIdInForUpdate(Set.of("old-primary", "new-primary")))
                .thenReturn(List.of(oldPrimary, newPrimary));
        when(projectMemberRepository.findByProject_IdAndUser_Id(7L, 2L))
                .thenReturn(Optional.of(oldMembership));
        when(projectMemberRepository.findByProject_IdAndUser_Id(7L, 3L)).thenReturn(Optional.empty());
        when(projectRepository.saveAndFlush(project)).thenReturn(project);
        when(projectMemberRepository.findActiveLeadersByProjectIds(List.of(7L)))
                .thenReturn(List.of(oldMembership, coMembership));
        ChangeProjectLeaderRequest request = new ChangeProjectLeaderRequest();
        request.setLeaderUserId("new-primary");

        ProjectResponse response = projectService.changeLeader(7L, request, admin.getEmail());

        assertThat(response.getPrimaryLeader().getUserId()).isEqualTo("new-primary");
        assertThat(response.getLeaders())
                .extracting(ProjectLeaderResponse::getUserId)
                .containsExactly("new-primary", "old-primary", "co-leader");
        assertThat(oldMembership.getProjectRole()).isEqualTo(ProjectRole.LEADER);
        assertThat(oldMembership.getStatus()).isEqualTo(ProjectMemberStatus.ACTIVE);
        assertThat(coMembership.getProjectRole()).isEqualTo(ProjectRole.LEADER);
    }

    @Test
    void changingPrimaryKeepsOldPrimaryAsProjectLeader() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        UserEntity oldPrimary = user(2L, "old-primary", "old@smartlab.test", true);
        UserEntity newPrimary = user(3L, "new-primary", "new@smartlab.test", true);
        ProjectEntity project = project(7L, oldPrimary, false, ProjectStatus.IN_PROGRESS);
        ProjectMemberEntity oldMembership = ProjectMemberEntity.createLeader(project, oldPrimary);
        stubAdmin(admin);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(userRepository.findAllByUserIdInForUpdate(Set.of("old-primary", "new-primary")))
                .thenReturn(List.of(oldPrimary, newPrimary));
        when(projectMemberRepository.findByProject_IdAndUser_Id(7L, 2L))
                .thenReturn(Optional.of(oldMembership));
        when(projectMemberRepository.findByProject_IdAndUser_Id(7L, 3L)).thenReturn(Optional.empty());
        when(projectRepository.saveAndFlush(project)).thenReturn(project);
        ChangeProjectLeaderRequest request = new ChangeProjectLeaderRequest();
        request.setLeaderUserId("new-primary");

        projectService.changeLeader(7L, request, admin.getEmail());

        assertThat(oldMembership.getProjectRole()).isEqualTo(ProjectRole.LEADER);
    }

    @Test
    void assignsFirstPrimaryLeaderToAnUnassignedProject() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        UserEntity newPrimary = user(2L, "new-primary", "new@smartlab.test", true);
        ProjectEntity project = project(7L, null, false, ProjectStatus.PROPOSED);
        stubAdmin(admin);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(userRepository.findAllByUserIdInForUpdate(Set.of("new-primary")))
                .thenReturn(List.of(newPrimary));
        when(projectMemberRepository.findByProject_IdAndUser_Id(7L, 2L)).thenReturn(Optional.empty());
        when(projectRepository.saveAndFlush(project)).thenReturn(project);
        ChangeProjectLeaderRequest request = new ChangeProjectLeaderRequest();
        request.setLeaderUserId("new-primary");

        ProjectResponse response = projectService.changeLeader(7L, request, admin.getEmail());

        assertThat(response.getPrimaryLeader().getUserId()).isEqualTo("new-primary");
        assertThat(response.getLeaders())
                .extracting(ProjectLeaderResponse::getUserId)
                .containsExactly("new-primary");
        verify(projectRepository).saveAndFlush(project);
    }

    @Test
    void assigningSamePrimaryHealsProjectMembership() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        UserEntity primary = user(2L, "primary-user", "primary@smartlab.test", true);
        ProjectEntity project = project(7L, primary, false, ProjectStatus.IN_PROGRESS);
        ProjectMemberEntity primaryMembership = ProjectMemberEntity.createLeader(project, primary);
        stubAdmin(admin);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(userRepository.findAllByUserIdInForUpdate(Set.of("primary-user")))
                .thenReturn(List.of(primary));
        when(projectMemberRepository.findByProject_IdAndUser_Id(7L, 2L))
                .thenReturn(Optional.of(primaryMembership));
        when(projectRepository.saveAndFlush(project)).thenReturn(project);
        when(projectMemberRepository.findActiveLeadersByProjectIds(List.of(7L)))
                .thenReturn(List.of(primaryMembership));
        ChangeProjectLeaderRequest request = new ChangeProjectLeaderRequest();
        request.setLeaderUserId("primary-user");

        ProjectResponse response = projectService.changeLeader(7L, request, admin.getEmail());

        assertThat(response.getPrimaryLeader().getUserId()).isEqualTo("primary-user");
        assertThat(primaryMembership.getProjectRole()).isEqualTo(ProjectRole.LEADER);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Iterable<ProjectMemberEntity>> memberships =
                ArgumentCaptor.forClass((Class) Iterable.class);
        verify(projectMemberRepository).saveAllAndFlush(memberships.capture());
        assertThat(memberships.getValue()).containsExactly(primaryMembership);
        verify(projectRepository).saveAndFlush(project);
    }

    @Test
    void replacingLeadersCanRemoveCurrentPrimaryAndLeaveSelectionForLater() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        UserEntity primary = user(2L, "primary-user", "primary@smartlab.test", true);
        UserEntity remaining = user(3L, "other-user", "other@smartlab.test", true);
        ProjectEntity project = project(7L, primary, false, ProjectStatus.IN_PROGRESS);
        ProjectMemberEntity primaryMembership = ProjectMemberEntity.createLeader(project, primary);
        stubAdmin(admin);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findAllByProject_IdAndProjectRoleAndStatus(
                7L,
                ProjectRole.LEADER,
                ProjectMemberStatus.ACTIVE
        )).thenReturn(List.of(primaryMembership));
        when(userRepository.findAllByUserIdInForUpdate(Set.of("primary-user", "other-user")))
                .thenReturn(List.of(primary, remaining));
        when(projectMemberRepository.findByProject_IdAndUser_Id(7L, 3L)).thenReturn(Optional.empty());
        when(projectRepository.saveAndFlush(project)).thenReturn(project);
        ChangeProjectLeadersRequest request = new ChangeProjectLeadersRequest();
        request.setLeaderUserIds(List.of("other-user"));

        ProjectResponse response = projectService.replaceLeaders(7L, request, admin.getEmail());

        assertThat(response.getPrimaryLeader()).isNull();
        assertThat(response.getLeaders())
                .extracting(ProjectLeaderResponse::getUserId)
                .containsExactly("other-user");
        assertThat(primaryMembership.getProjectRole()).isEqualTo(ProjectRole.MEMBER);
        verify(projectRepository).saveAndFlush(project);
    }

    @Test
    void emptyLeaderListClearsPrimaryAndEveryLeader() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        UserEntity primary = user(2L, "primary-user", "primary@smartlab.test", true);
        UserEntity coLeader = user(3L, "co-user", "co@smartlab.test", true);
        ProjectEntity project = project(7L, primary, false, ProjectStatus.PROPOSED);
        ProjectMemberEntity primaryMembership = ProjectMemberEntity.createLeader(project, primary);
        ProjectMemberEntity coMembership = ProjectMemberEntity.createLeader(project, coLeader);
        stubAdmin(admin);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findAllByProject_IdAndProjectRoleAndStatus(
                7L,
                ProjectRole.LEADER,
                ProjectMemberStatus.ACTIVE
        )).thenReturn(List.of(primaryMembership, coMembership));
        when(userRepository.findAllByUserIdInForUpdate(Set.of("primary-user", "co-user")))
                .thenReturn(List.of(primary, coLeader));
        when(projectRepository.saveAndFlush(project)).thenReturn(project);
        ChangeProjectLeadersRequest request = new ChangeProjectLeadersRequest();
        request.setLeaderUserIds(List.of());

        ProjectResponse response = projectService.replaceLeaders(7L, request, admin.getEmail());

        assertThat(response.getPrimaryLeader()).isNull();
        assertThat(response.getLeaders()).isEmpty();
        assertThat(primaryMembership.getProjectRole()).isEqualTo(ProjectRole.MEMBER);
        assertThat(coMembership.getProjectRole()).isEqualTo(ProjectRole.MEMBER);
    }

    @Test
    void replacingLeadersDemotesRemovedCoLeaderAndPromotesExistingMember() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        UserEntity primary = user(2L, "primary-user", "primary@smartlab.test", true);
        UserEntity removed = user(3L, "removed-user", "removed@smartlab.test", true);
        UserEntity added = user(4L, "added-user", "added@smartlab.test", true);
        ProjectEntity project = project(7L, primary, false, ProjectStatus.IN_PROGRESS);
        ProjectMemberEntity primaryMembership = ProjectMemberEntity.createLeader(project, primary);
        ProjectMemberEntity removedMembership = ProjectMemberEntity.createLeader(project, removed);
        ProjectMemberEntity addedMembership = ProjectMemberEntity.createMember(project, added);
        stubAdmin(admin);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findAllByProject_IdAndProjectRoleAndStatus(
                7L,
                ProjectRole.LEADER,
                ProjectMemberStatus.ACTIVE
        )).thenReturn(List.of(primaryMembership, removedMembership));
        when(userRepository.findAllByUserIdInForUpdate(Set.of(
                "primary-user",
                "removed-user",
                "added-user"
        ))).thenReturn(List.of(primary, removed, added));
        when(projectMemberRepository.findByProject_IdAndUser_Id(7L, 4L))
                .thenReturn(Optional.of(addedMembership));
        ChangeProjectLeadersRequest request = new ChangeProjectLeadersRequest();
        request.setLeaderUserIds(List.of("primary-user", "added-user"));

        ProjectResponse response = projectService.replaceLeaders(7L, request, admin.getEmail());

        assertThat(response.getLeaders())
                .extracting(ProjectLeaderResponse::getUserId)
                .containsExactly("primary-user", "added-user");
        assertThat(addedMembership.getProjectRole()).isEqualTo(ProjectRole.LEADER);
        assertThat(removedMembership.getProjectRole()).isEqualTo(ProjectRole.MEMBER);
        assertThat(removedMembership.getStatus()).isEqualTo(ProjectMemberStatus.ACTIVE);
    }

    @Test
    void atomicLeadershipUpdateSelectsPrimaryAndDemotesEveryRemovedLeader() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        UserEntity oldPrimary = user(2L, "old-primary", "old@smartlab.test", true);
        UserEntity removed = user(3L, "removed-user", "removed@smartlab.test", true);
        UserEntity selected = user(4L, "selected-user", "selected@smartlab.test", true);
        ProjectEntity project = project(7L, oldPrimary, false, ProjectStatus.IN_PROGRESS);
        ProjectMemberEntity oldPrimaryMembership = ProjectMemberEntity.createLeader(project, oldPrimary);
        ProjectMemberEntity removedMembership = ProjectMemberEntity.createLeader(project, removed);
        ProjectMemberEntity selectedMembership = ProjectMemberEntity.createMember(project, selected);
        stubAdmin(admin);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findAllByProject_IdAndProjectRoleAndStatus(
                7L,
                ProjectRole.LEADER,
                ProjectMemberStatus.ACTIVE
        )).thenReturn(List.of(oldPrimaryMembership, removedMembership));
        when(userRepository.findAllByUserIdInForUpdate(Set.of(
                "old-primary",
                "removed-user",
                "selected-user"
        ))).thenReturn(List.of(oldPrimary, removed, selected));
        when(projectMemberRepository.findByProject_IdAndUser_Id(7L, 4L))
                .thenReturn(Optional.of(selectedMembership));
        when(projectRepository.saveAndFlush(project)).thenReturn(project);
        UpdateProjectLeadershipRequest request = new UpdateProjectLeadershipRequest();
        request.setPrimaryLeaderUserId("selected-user");
        request.setLeaderUserIds(List.of("selected-user"));

        ProjectResponse response = projectService.updateLeadership(7L, request, admin.getEmail());

        assertThat(response.getPrimaryLeader().getUserId()).isEqualTo("selected-user");
        assertThat(response.getLeaders())
                .extracting(ProjectLeaderResponse::getUserId)
                .containsExactly("selected-user");
        assertThat(selectedMembership.getProjectRole()).isEqualTo(ProjectRole.LEADER);
        assertThat(oldPrimaryMembership.getProjectRole()).isEqualTo(ProjectRole.MEMBER);
        assertThat(oldPrimaryMembership.getStatus()).isEqualTo(ProjectMemberStatus.ACTIVE);
        assertThat(removedMembership.getProjectRole()).isEqualTo(ProjectRole.MEMBER);
        assertThat(removedMembership.getStatus()).isEqualTo(ProjectMemberStatus.ACTIVE);
        verify(projectRepository).saveAndFlush(project);
    }

    @Test
    void atomicLeadershipUpdateCanClearPrimaryAndCompleteLeaderSet() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        UserEntity primary = user(2L, "primary-user", "primary@smartlab.test", true);
        ProjectEntity project = project(7L, primary, false, ProjectStatus.IN_PROGRESS);
        ProjectMemberEntity primaryMembership = ProjectMemberEntity.createLeader(project, primary);
        stubAdmin(admin);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findAllByProject_IdAndProjectRoleAndStatus(
                7L,
                ProjectRole.LEADER,
                ProjectMemberStatus.ACTIVE
        )).thenReturn(List.of(primaryMembership));
        when(userRepository.findAllByUserIdInForUpdate(Set.of("primary-user")))
                .thenReturn(List.of(primary));
        when(projectRepository.saveAndFlush(project)).thenReturn(project);
        UpdateProjectLeadershipRequest request = new UpdateProjectLeadershipRequest();
        request.setLeaderUserIds(List.of());

        ProjectResponse response = projectService.updateLeadership(7L, request, admin.getEmail());

        assertThat(response.getPrimaryLeader()).isNull();
        assertThat(response.getLeaders()).isEmpty();
        assertThat(primaryMembership.getProjectRole()).isEqualTo(ProjectRole.MEMBER);
        assertThat(primaryMembership.getStatus()).isEqualTo(ProjectMemberStatus.ACTIVE);
    }

    @Test
    void atomicLeadershipUpdateRejectsPrimaryOutsideLeaderSetEvenWithoutControllerValidation() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        ProjectEntity project = project(7L, null, false, ProjectStatus.PROPOSED);
        stubAdmin(admin);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        UpdateProjectLeadershipRequest request = new UpdateProjectLeadershipRequest();
        request.setPrimaryLeaderUserId("outside-user");
        request.setLeaderUserIds(List.of("leader-user"));

        assertStatus(
                () -> projectService.updateLeadership(7L, request, admin.getEmail()),
                HttpStatus.BAD_REQUEST
        );

        verify(projectMemberRepository, never()).saveAllAndFlush(any());
        verify(projectRepository, never()).saveAndFlush(any(ProjectEntity.class));
    }

    @Test
    void atomicLeadershipUpdateRejectsDuplicateIdsBeforeAnyMembershipMutation() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        ProjectEntity project = project(7L, null, false, ProjectStatus.PROPOSED);
        stubAdmin(admin);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        UpdateProjectLeadershipRequest request = new UpdateProjectLeadershipRequest();
        request.setLeaderUserIds(List.of("leader-user", " leader-user "));

        assertStatus(
                () -> projectService.updateLeadership(7L, request, admin.getEmail()),
                HttpStatus.BAD_REQUEST
        );

        verify(projectMemberRepository, never()).saveAllAndFlush(any());
        verify(projectRepository, never()).saveAndFlush(any(ProjectEntity.class));
    }

    @Test
    void atomicLeadershipUpdateRejectsUnknownAccountBeforeAnyMembershipMutation() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        ProjectEntity project = project(7L, null, false, ProjectStatus.PROPOSED);
        stubAdmin(admin);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(userRepository.findAllByUserIdInForUpdate(Set.of("unknown-user")))
                .thenReturn(List.of());
        UpdateProjectLeadershipRequest request = new UpdateProjectLeadershipRequest();
        request.setLeaderUserIds(List.of("unknown-user"));

        assertStatus(
                () -> projectService.updateLeadership(7L, request, admin.getEmail()),
                HttpStatus.NOT_FOUND
        );

        verify(projectMemberRepository, never()).saveAllAndFlush(any());
        verify(projectRepository, never()).saveAndFlush(any(ProjectEntity.class));
    }

    @Test
    void atomicLeadershipUpdateRejectsInactiveAccountBeforeAnyMembershipMutation() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        UserEntity inactive = user(2L, "inactive-user", "inactive@smartlab.test", false);
        ProjectEntity project = project(7L, null, false, ProjectStatus.PROPOSED);
        stubAdmin(admin);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(userRepository.findAllByUserIdInForUpdate(Set.of("inactive-user")))
                .thenReturn(List.of(inactive));
        UpdateProjectLeadershipRequest request = new UpdateProjectLeadershipRequest();
        request.setLeaderUserIds(List.of("inactive-user"));

        assertStatus(
                () -> projectService.updateLeadership(7L, request, admin.getEmail()),
                HttpStatus.BAD_REQUEST
        );

        verify(projectMemberRepository, never()).saveAllAndFlush(any());
        verify(projectRepository, never()).saveAndFlush(any(ProjectEntity.class));
    }

    @Test
    void softDeleteDoesNotChangeProjectLeaderMemberships() {
        UserEntity admin = user(1L, "admin-user", "admin@smartlab.test", true);
        UserEntity primary = user(2L, "primary-user", "primary@smartlab.test", true);
        UserEntity coLeader = user(3L, "co-user", "co@smartlab.test", true);
        ProjectEntity project = project(7L, primary, false, ProjectStatus.IN_PROGRESS);
        ProjectMemberEntity primaryMembership = ProjectMemberEntity.createLeader(project, primary);
        ProjectMemberEntity coMembership = ProjectMemberEntity.createLeader(project, coLeader);
        stubAdmin(admin);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));

        projectService.delete(7L, admin.getEmail());

        assertThat(project.getDeletedAt()).isNotNull();
        assertThat(primaryMembership.getProjectRole()).isEqualTo(ProjectRole.LEADER);
        assertThat(coMembership.getProjectRole()).isEqualTo(ProjectRole.LEADER);
        verify(projectRepository).saveAndFlush(project);
        verify(projectRepository, never()).delete(any(ProjectEntity.class));
    }

    @Test
    void activeMemberWithProjectReadCanViewPrivateProject() {
        UserEntity primary = user(2L, "primary-user", "primary@smartlab.test", true);
        UserEntity member = user(3L, "member-user", "member@smartlab.test", true);
        ProjectEntity project = project(7L, primary, false, ProjectStatus.IN_PROGRESS);
        when(projectRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(project));
        when(userRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
        when(permissionService.getRoleCodes(member)).thenReturn(Set.of("MEMBER"));
        when(permissionService.getEffectivePermissionCodes(member)).thenReturn(Set.of("PROJECT_READ"));
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                7L,
                3L,
                ProjectMemberStatus.ACTIVE
        )).thenReturn(true);

        ProjectResponse response = projectService.get(7L, member.getEmail());

        assertThat(response.getId()).isEqualTo(7L);
        assertThat(response.getPrimaryLeader().getUserId()).isEqualTo("primary-user");
    }

    private void stubAdmin(UserEntity admin) {
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(permissionService.getRoleCodes(admin)).thenReturn(Set.of("ADMIN"));
        when(permissionService.getEffectivePermissionCodes(admin)).thenReturn(Set.of("PROJECT_MANAGE"));
    }

    private static CreateProjectRequest minimalCreateRequest(String code, String name, String leaderUserId) {
        CreateProjectRequest request = new CreateProjectRequest();
        request.setCode(code);
        request.setName(name);
        request.setLeaderUserId(leaderUserId);
        return request;
    }

    private static ProjectEntity project(
            Long id,
            UserEntity leader,
            boolean isPublic,
            ProjectStatus status
    ) {
        ProjectEntity project = ProjectEntity.create(
                "SL-" + id,
                "Project " + id,
                null,
                null,
                ProjectType.RESEARCH,
                leader,
                status,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 1),
                null,
                isPublic,
                false,
                user(1L, "admin-user", "admin@smartlab.test", true)
        );
        ReflectionTestUtils.setField(project, "id", id);
        return project;
    }

    private static UserEntity user(Long id, String userId, String email, boolean active) {
        return UserEntity.builder()
                .id(id)
                .userId(userId)
                .name(userId)
                .email(email)
                .isActive(active)
                .build();
    }

    private static void assertStatus(Runnable action, HttpStatus expectedStatus) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(expectedStatus));
    }
}
