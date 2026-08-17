package com.smartlab.service.impl;

import com.smartlab.entity.ResearchFieldEntity;
import com.smartlab.repo.ResearchFieldRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchFieldServiceImplTest {
    @Mock ResearchFieldRepository researchFieldRepository;
    @InjectMocks ResearchFieldServiceImpl service;

    @Test
    void deactivatesExistingFieldWithoutPhysicallyDeletingIt() {
        ResearchFieldEntity field = field(7L);
        when(researchFieldRepository.findById(7L)).thenReturn(Optional.of(field));

        service.delete(7L);

        assertThat(field.getIsActive()).isFalse();
        verify(researchFieldRepository).save(field);
        verify(researchFieldRepository, never()).delete(field);
    }

    @Test
    void rejectsDeletingMissingField() {
        when(researchFieldRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private ResearchFieldEntity field(Long id) {
        return ResearchFieldEntity.builder()
                .id(id)
                .code("AI")
                .name("Artificial Intelligence")
                .isActive(true)
                .build();
    }
}
