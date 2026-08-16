package com.smartlab.service.impl;

import com.smartlab.dto.request.ReplaceProjectResearchFieldsRequest;
import com.smartlab.dto.response.ProjectResponse;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.ResearchFieldEntity;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.ProjectResearchFieldRepository;
import com.smartlab.repo.ResearchFieldRepository;
import com.smartlab.service.ProjectAccessService;
import com.smartlab.service.ProjectResearchFieldGateway;
import com.smartlab.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectResearchFieldServiceImplTest {
    @Mock private ProjectRepository projectRepository;
    @Mock private ResearchFieldRepository researchFieldRepository;
    @Mock private ProjectResearchFieldRepository relationRepository;
    @Mock private ProjectResearchFieldGateway gateway;
    @Mock private ProjectAccessService accessService;
    @Mock private ProjectService projectService;
    @InjectMocks private ProjectResearchFieldServiceImpl service;

    @Test
    void replacesFullSetAndReturnsStableGatewayOrder() {
        ProjectEntity project = org.mockito.Mockito.mock(ProjectEntity.class);
        ReplaceProjectResearchFieldsRequest request = request(List.of(2L, 1L));
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(gateway.getProjectFields(7L)).thenReturn(List.of(
                new ProjectResearchFieldGateway.ResearchFieldReference(1L, "AI", "Artificial Intelligence", true),
                new ProjectResearchFieldGateway.ResearchFieldReference(2L, "IOT", "Internet of Things", true)
        ));

        var response = service.replace(7L, request, "leader@test");

        verify(accessService).requireManage(project, "leader@test");
        verify(gateway).replaceProjectFields(7L, Set.of(2L, 1L));
        assertThat(response).extracting("id").containsExactly(1L, 2L);
    }

    @Test
    void emptyListClearsEveryField() {
        ProjectEntity project = org.mockito.Mockito.mock(ProjectEntity.class);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(gateway.getProjectFields(7L)).thenReturn(List.of());

        assertThat(service.replace(7L, request(List.of()), "leader@test")).isEmpty();

        verify(gateway).replaceProjectFields(7L, Set.of());
    }

    @Test
    void rejectsDuplicateIdsBeforeChangingRelations() {
        ProjectEntity project = org.mockito.Mockito.mock(ProjectEntity.class);
        when(projectRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(project));

        assertStatus(() -> service.replace(7L, request(List.of(1L, 1L)), "leader@test"), HttpStatus.BAD_REQUEST);

        verify(gateway, never()).replaceProjectFields(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void filtersOnlyAlreadyVisibleProjectsWithoutChangingTheirOrder() {
        ResearchFieldEntity active = ResearchFieldEntity.builder()
                .code("AI").name("AI").isActive(true).build();
        ProjectResponse first = ProjectResponse.builder().id(1L).build();
        ProjectResponse second = ProjectResponse.builder().id(2L).build();
        when(researchFieldRepository.findById(9L)).thenReturn(Optional.of(active));
        when(relationRepository.findActiveProjectIdsByFieldId(9L)).thenReturn(List.of(2L));
        when(projectService.list("member@test")).thenReturn(List.of(first, second));

        List<ProjectResponse> response = service.filterVisibleProjects(9L, "member@test");

        assertThat(response).containsExactly(second);
    }

    @Test
    void rejectsInactiveFilterField() {
        ResearchFieldEntity inactive = ResearchFieldEntity.builder()
                .code("OLD").name("Old").isActive(false).build();
        when(researchFieldRepository.findById(9L)).thenReturn(Optional.of(inactive));

        assertStatus(() -> service.filterVisibleProjects(9L, null), HttpStatus.NOT_FOUND);

        verify(projectService, never()).list(org.mockito.ArgumentMatchers.any());
    }

    private ReplaceProjectResearchFieldsRequest request(List<Long> ids) {
        ReplaceProjectResearchFieldsRequest request = new ReplaceProjectResearchFieldsRequest();
        request.setFieldIds(ids);
        return request;
    }

    private void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, HttpStatus status) {
        assertThatThrownBy(callable)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(status);
    }
}
