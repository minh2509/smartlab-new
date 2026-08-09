package com.smartlab.service.impl;

import com.smartlab.dto.response.ContentCategoryResponse;
import com.smartlab.entity.ContentCategoryEntity;
import com.smartlab.repo.ContentCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentCategoryServiceImplTest {

    @Mock
    private ContentCategoryRepository contentCategoryRepository;

    @InjectMocks
    private ContentCategoryServiceImpl contentCategoryService;

    @Test
    void returnsActiveCategoriesInRepositoryOrderAndMapsResponseFields() {
        when(contentCategoryRepository.findByIsActiveTrueOrderByIdAsc()).thenReturn(List.of(
                category(2L, "LAB_ANNOUNCEMENT", "Lab Announcement", "Lab updates"),
                category(9L, "EVENT_CONTENT", "Event Content", null)
        ));

        List<ContentCategoryResponse> categories = contentCategoryService.getActiveCategories();

        assertThat(categories)
                .extracting(ContentCategoryResponse::getId,
                        ContentCategoryResponse::getCode,
                        ContentCategoryResponse::getName,
                        ContentCategoryResponse::getDescription)
                .containsExactly(
                        tuple(2L, "LAB_ANNOUNCEMENT", "Lab Announcement", "Lab updates"),
                        tuple(9L, "EVENT_CONTENT", "Event Content", null)
                );
        verify(contentCategoryRepository).findByIsActiveTrueOrderByIdAsc();
    }

    @Test
    void returnsEmptyListWhenThereAreNoActiveCategories() {
        when(contentCategoryRepository.findByIsActiveTrueOrderByIdAsc()).thenReturn(List.of());

        assertThat(contentCategoryService.getActiveCategories()).isEmpty();
    }

    private static ContentCategoryEntity category(Long id, String code, String name, String description) {
        return ContentCategoryEntity.builder()
                .id(id)
                .code(code)
                .name(name)
                .description(description)
                .isActive(true)
                .build();
    }
}
