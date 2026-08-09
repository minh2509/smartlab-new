package com.smartlab.controller;

import com.smartlab.dto.request.CreateContentCategoryRequest;
import com.smartlab.dto.response.ContentCategoryResponse;
import com.smartlab.service.ContentCategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContentCategoryControllerTest {

    @Mock
    private ContentCategoryService contentCategoryService;

    @InjectMocks
    private ContentCategoryController contentCategoryController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(contentCategoryController).build();
    }

    @Test
    void getsActiveCategoriesAsJsonCollection() throws Exception {
        when(contentCategoryService.getActiveCategories()).thenReturn(List.of(
                ContentCategoryResponse.builder()
                        .id(2L)
                        .code("LAB_ANNOUNCEMENT")
                        .name("Lab Announcement")
                        .description("Lab updates")
                        .build()
        ));

        mockMvc.perform(get("/content-categories"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].code").value("LAB_ANNOUNCEMENT"))
                .andExpect(jsonPath("$[0].name").value("Lab Announcement"))
                .andExpect(jsonPath("$[0].description").value("Lab updates"));
    }

    @Test
    void hasNoPostHandlerForContentCategories() throws Exception {
        mockMvc.perform(post("/content-categories"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void createsCategoryWithOkResponse() throws Exception {
        when(contentCategoryService.createCategory(any())).thenReturn(
                ContentCategoryResponse.builder()
                        .id(7L)
                        .code("MEMBER_BLOG")
                        .name("Member Blog")
                        .description("Member updates")
                        .build()
        );

        mockMvc.perform(post("/admin/content-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"MEMBER_BLOG","name":"Member Blog","description":"Member updates"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.code").value("MEMBER_BLOG"))
                .andExpect(jsonPath("$.name").value("Member Blog"))
                .andExpect(jsonPath("$.description").value("Member updates"));

        ArgumentCaptor<CreateContentCategoryRequest> request = ArgumentCaptor.forClass(CreateContentCategoryRequest.class);
        verify(contentCategoryService).createCategory(request.capture());
        assertThat(request.getValue())
                .extracting(CreateContentCategoryRequest::getCode,
                        CreateContentCategoryRequest::getName,
                        CreateContentCategoryRequest::getDescription)
                .containsExactly("MEMBER_BLOG", "Member Blog", "Member updates");
    }

    @Test
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/admin/content-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"","name":"Member Blog"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void propagatesKnownDuplicateConflict() throws Exception {
        when(contentCategoryService.createCategory(any())).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Category code already exists")
        );

        mockMvc.perform(post("/admin/content-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"NEWS","name":"News"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void hasNoExtraCategoryPostRoute() throws Exception {
        mockMvc.perform(post("/admin/content-categories/extra"))
                .andExpect(status().isNotFound());
    }
}
