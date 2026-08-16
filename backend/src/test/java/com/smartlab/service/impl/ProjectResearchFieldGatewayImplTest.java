package com.smartlab.service.impl;

import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.ProjectResearchFieldEntity;
import com.smartlab.entity.ResearchFieldEntity;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.ProjectResearchFieldRepository;
import com.smartlab.repo.ResearchFieldRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectResearchFieldGatewayImplTest {
    @Mock private ProjectResearchFieldRepository relationRepository;
    @Mock private ResearchFieldRepository researchFieldRepository;
    @Mock private ProjectRepository projectRepository;
    @InjectMocks private ProjectResearchFieldGatewayImpl gateway;

    @Test
    void replacesRelationsByDifferenceAndRetainsAttachedInactiveField() {
        ProjectEntity project = org.mockito.Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(7L);
        ResearchFieldEntity inactiveAttached = field(1L, false);
        ResearchFieldEntity activeNew = field(2L, true);
        ProjectResearchFieldEntity existing = ProjectResearchFieldEntity.create(project, inactiveAttached);
        when(relationRepository.findAllWithFieldByProjectId(7L)).thenReturn(List.of(existing));
        when(researchFieldRepository.findAllById(Set.of(1L, 2L)))
                .thenReturn(List.of(inactiveAttached, activeNew));
        when(projectRepository.getReferenceById(7L)).thenReturn(project);

        gateway.replaceProjectFields(7L, Set.of(1L, 2L));

        verify(relationRepository, never()).deleteAll(anyList());
        verify(relationRepository).saveAllAndFlush(org.mockito.ArgumentMatchers.argThat(relations -> {
            ProjectResearchFieldEntity added = relations.iterator().next();
            return added.getResearchField().getId().equals(2L);
        }));
    }

    @Test
    void rejectsNewInactiveField() {
        ResearchFieldEntity inactive = field(3L, false);
        when(relationRepository.findAllWithFieldByProjectId(7L)).thenReturn(List.of());
        when(researchFieldRepository.findAllById(Set.of(3L))).thenReturn(List.of(inactive));

        assertThatThrownBy(() -> gateway.replaceProjectFields(7L, Set.of(3L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(relationRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void clearsEveryRelationForEmptyReplacement() {
        ProjectEntity project = org.mockito.Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(7L);
        ProjectResearchFieldEntity existing = ProjectResearchFieldEntity.create(project, field(1L, true));
        when(relationRepository.findAllWithFieldByProjectId(7L)).thenReturn(List.of(existing));
        when(projectRepository.getReferenceById(7L)).thenReturn(project);

        gateway.replaceProjectFields(7L, Set.of());

        verify(relationRepository).deleteAll(List.of(existing));
        verify(relationRepository).flush();
        verify(relationRepository, never()).saveAllAndFlush(anyList());
    }

    private ResearchFieldEntity field(Long id, boolean active) {
        ResearchFieldEntity field = ResearchFieldEntity.builder()
                .code("F" + id).name("Field " + id).isActive(active).build();
        ReflectionTestUtils.setField(field, "id", id);
        return field;
    }
}
