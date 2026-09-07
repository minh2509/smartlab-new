package com.smartlab.controller;

import com.smartlab.dto.request.UpdateLabArticleRequest;
import com.smartlab.dto.response.AdminLabArticleResponse;
import com.smartlab.dto.response.PublicLabArticleDetailResponse;
import com.smartlab.dto.response.PublicLabArticleSummaryResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.LabArticleStatus;
import com.smartlab.service.LabArticleService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LabArticleControllerTest {
    private final LabArticleService articleService = org.mockito.Mockito.mock(LabArticleService.class);
    private final LabArticleController controller = new LabArticleController(articleService);

    @Test void publicContractsForwardDefaultsAndDoNotLeakAdminFields() throws Exception {
        PublicLabArticleSummaryResponse summary = new PublicLabArticleSummaryResponse(7L, "Article", "article", "Excerpt", Instant.parse("2026-08-20T00:00:00Z"));
        when(articleService.listLatest(3)).thenReturn(List.of(summary)); when(articleService.listArchive(0, 12)).thenReturn(new PublicPageResponse<>(List.of(summary), 0, 12, 1, 1));
        when(articleService.getPublicBySlug("article")).thenReturn(new PublicLabArticleDetailResponse(7L, "Article", "article", "Excerpt", Map.of("type", "doc", "body", "Body"), Instant.now()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/articles/latest")).andExpect(status().isOk()).andExpect(jsonPath("$[0].status").doesNotExist());
        mvc.perform(get("/articles")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].createdAt").doesNotExist());
        mvc.perform(get("/articles/article")).andExpect(status().isOk()).andExpect(jsonPath("$.status").doesNotExist()).andExpect(jsonPath("$.deletedAt").doesNotExist());
        verify(articleService).listLatest(3); verify(articleService).listArchive(0, 12); verify(articleService).getPublicBySlug("article");
    }

    @Test void patchTracksExplicitNullExcerptWithoutClientSpoofablePresenceFlag() throws Exception {
        when(articleService.update(eq(7L), any())).thenReturn(admin());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(patch("/admin/articles/7").contentType(MediaType.APPLICATION_JSON).content("{\"excerpt\":null,\"excerptPresent\":false}"))
                .andExpect(status().isOk());
        ArgumentCaptor<UpdateLabArticleRequest> update = ArgumentCaptor.forClass(UpdateLabArticleRequest.class);
        verify(articleService).update(eq(7L), update.capture()); assertThat(update.getValue().hasExcerpt()).isTrue();
    }

    private static AdminLabArticleResponse admin() { return new AdminLabArticleResponse(7L, "Article", "article", null, Map.of("type", "doc"), LabArticleStatus.DRAFT, null, Instant.now(), Instant.now()); }
}
