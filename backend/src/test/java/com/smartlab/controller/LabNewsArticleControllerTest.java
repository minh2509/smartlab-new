package com.smartlab.controller;

import com.smartlab.dto.request.CreateLabNewsArticleRequest;
import com.smartlab.dto.request.UpdateLabNewsArticleRequest;
import com.smartlab.dto.response.LabNewsArticleResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.PublicNewsSort;
import com.smartlab.service.LabNewsArticleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LabNewsArticleControllerTest {
    @Mock private LabNewsArticleService articleService;
    @InjectMocks private LabNewsArticleController controller;

    @Test void publicListDefaultsToThreeForwardsExplicitLimitAndHidesInternalFields() throws Exception {
        when(articleService.listPublic(3)).thenReturn(List.of(response()));
        when(articleService.listPublic(5)).thenReturn(List.of(response()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/news")).andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Article"))
                .andExpect(jsonPath("$[0].isPublic").doesNotExist()).andExpect(jsonPath("$[0].deletedAt").doesNotExist());
        mvc.perform(get("/news").queryParam("limit", "5")).andExpect(status().isOk());
        verify(articleService).listPublic(3); verify(articleService).listPublic(5);
    }

    @Test void adminListForwardsServerPagingAndDoesNotExposeDeletedAt() throws Exception {
        when(articleService.listAdmin(1, 20)).thenReturn(new PublicPageResponse<>(List.of(response()), 1, 20, 21, 2));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/admin/news").queryParam("page", "1").queryParam("size", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].isPublic").value(true))
                .andExpect(jsonPath("$.items[0].deletedAt").doesNotExist())
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(20));
        verify(articleService).listAdmin(1, 20);
    }

    @Test void publicArchiveDefaultsForwardsPagingAndHidesInternalFields() throws Exception {
        when(articleService.listPublicArchive(null, null, null, PublicNewsSort.LATEST, 0, 12)).thenReturn(new PublicPageResponse<>(List.of(response()), 0, 12, 13, 2));
        when(articleService.listPublicArchive("robot", "VnExpress", 2026, PublicNewsSort.OLDEST, 2, 8)).thenReturn(new PublicPageResponse<>(List.of(response()), 2, 8, 24, 3));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/news/archive")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].title").value("Article"))
                .andExpect(jsonPath("$.items[0].isPublic").doesNotExist()).andExpect(jsonPath("$.items[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.items[0].updatedAt").doesNotExist()).andExpect(jsonPath("$.items[0].deletedAt").doesNotExist())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(12));
        mvc.perform(get("/news/archive").queryParam("q", "robot").queryParam("source", "VnExpress")
                        .queryParam("year", "2026").queryParam("sort", "OLDEST").queryParam("page", "2").queryParam("size", "8"))
                .andExpect(status().isOk());
        verify(articleService).listPublicArchive(null, null, null, PublicNewsSort.LATEST, 0, 12);
        verify(articleService).listPublicArchive("robot", "VnExpress", 2026, PublicNewsSort.OLDEST, 2, 8);
    }

    @Test void publicOptionsExposeSourcesAndYears() throws Exception {
        when(articleService.listPublicSources()).thenReturn(List.of("SmartLab", "VnExpress"));
        when(articleService.listPublicYears()).thenReturn(List.of(2026, 2025));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/news/archive/sources")).andExpect(status().isOk()).andExpect(jsonPath("$[0]").value("SmartLab"));
        mvc.perform(get("/news/archive/years")).andExpect(status().isOk()).andExpect(jsonPath("$[0]").value(2026));
        verify(articleService).listPublicSources(); verify(articleService).listPublicYears();
    }

    @Test void createValidationRejectsMissingTitleSourceNameSourceUrlAndPublishedAt() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/admin/news").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void patchTracksExplicitNullExcerptWithoutClientSpoofablePresenceFlags() throws Exception {
        when(articleService.update(eq(7L), any())).thenReturn(response());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(patch("/admin/news/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"excerpt\":null,\"excerptPresent\":false}"))
                .andExpect(status().isOk());
        ArgumentCaptor<UpdateLabNewsArticleRequest> update = ArgumentCaptor.forClass(UpdateLabNewsArticleRequest.class);
        verify(articleService).update(eq(7L), update.capture());
        assertThat(update.getValue().isExcerptPresent()).isTrue();
    }

    private static LabNewsArticleResponse response() {
        return new LabNewsArticleResponse(7L, "Article", "Excerpt", "FPT Education", "https://example.test/article",
                Instant.parse("2026-08-20T00:00:00Z"), true, Instant.parse("2026-08-19T00:00:00Z"),
                Instant.parse("2026-08-20T00:00:00Z"), Instant.parse("2026-08-21T00:00:00Z"));
    }
}
