package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateContentCategoryRequest;
import com.smartlab.dto.response.ContentCategoryResponse;
import com.smartlab.entity.ContentCategoryEntity;
import com.smartlab.repo.ContentCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    @Test
    void createsAnActiveCategoryAndMapsPersistedResponse() {
        CreateContentCategoryRequest request = request("MEMBER_BLOG", "Member Blog", "Member updates");
        when(contentCategoryRepository.existsByCode("MEMBER_BLOG")).thenReturn(false);
        when(contentCategoryRepository.save(any(ContentCategoryEntity.class))).thenReturn(
                category(7L, "MEMBER_BLOG", "Member Blog", "Member updates")
        );

        ContentCategoryResponse response = contentCategoryService.createCategory(request);

        assertThat(response)
                .extracting(ContentCategoryResponse::getId,
                        ContentCategoryResponse::getCode,
                        ContentCategoryResponse::getName,
                        ContentCategoryResponse::getDescription)
                .containsExactly(7L, "MEMBER_BLOG", "Member Blog", "Member updates");
        ArgumentCaptor<ContentCategoryEntity> savedCategory = ArgumentCaptor.forClass(ContentCategoryEntity.class);
        verify(contentCategoryRepository).save(savedCategory.capture());
        assertThat(savedCategory.getValue())
                .extracting(ContentCategoryEntity::getCode,
                        ContentCategoryEntity::getName,
                        ContentCategoryEntity::getDescription,
                        ContentCategoryEntity::getIsActive)
                .containsExactly("MEMBER_BLOG", "Member Blog", "Member updates", true);
    }

    @Test
    void acceptsNullDescription() {
        CreateContentCategoryRequest request = request("NEWS", "News", null);
        when(contentCategoryRepository.existsByCode("NEWS")).thenReturn(false);
        when(contentCategoryRepository.save(any(ContentCategoryEntity.class))).thenReturn(
                category(3L, "NEWS", "News", null)
        );

        ContentCategoryResponse response = contentCategoryService.createCategory(request);

        assertThat(response.getDescription()).isNull();
        ArgumentCaptor<ContentCategoryEntity> savedCategory = ArgumentCaptor.forClass(ContentCategoryEntity.class);
        verify(contentCategoryRepository).save(savedCategory.capture());
        assertThat(savedCategory.getValue().getDescription()).isNull();
    }

    @Test
    void rejectsKnownDuplicateCodeWithoutSaving() {
        CreateContentCategoryRequest request = request("NEWS", "News", null);
        when(contentCategoryRepository.existsByCode("NEWS")).thenReturn(true);

        assertThatThrownBy(() -> contentCategoryService.createCategory(request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(contentCategoryRepository, never()).save(any(ContentCategoryEntity.class));
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

    private static CreateContentCategoryRequest request(String code, String name, String description) {
        CreateContentCategoryRequest request = new CreateContentCategoryRequest();
        request.setCode(code);
        request.setName(name);
        request.setDescription(description);
        return request;
    }
}
