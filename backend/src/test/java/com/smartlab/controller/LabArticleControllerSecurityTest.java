package com.smartlab.controller;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
import com.smartlab.dto.request.CreateLabArticleRequest;
import com.smartlab.dto.request.UpdateLabArticleRequest;
import com.smartlab.dto.response.AdminLabArticleResponse;
import com.smartlab.dto.response.PublicLabArticleDetailResponse;
import com.smartlab.dto.response.PublicLabArticleSummaryResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.LabArticleStatus;
import com.smartlab.enums.PublicArticleSort;
import com.smartlab.filter.JwtRequestFilter;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.LabArticleService;
import com.smartlab.service.UserSessionService;
import com.smartlab.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LabArticleController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, JwtRequestFilter.class})
class LabArticleControllerSecurityTest {
    private static final String BODY = "{\"title\":\"Article\",\"content\":{\"type\":\"doc\",\"body\":\"Body\"}}";
    @Autowired private MockMvc mockMvc;
    @MockitoBean private LabArticleService articleService;
    @MockitoBean private AppUserDetailService appUserDetailService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserSessionService userSessionService;

    @Test void anonymousCanReadOnlyPublicArticleEndpoints() throws Exception {
        when(articleService.listLatest(3)).thenReturn(List.of(summary())); when(articleService.listArchive(null, null, PublicArticleSort.LATEST, 0, 12)).thenReturn(new PublicPageResponse<>(List.of(summary()), 0, 12, 1, 1)); when(articleService.listPublishedYears()).thenReturn(List.of(2026)); when(articleService.getPublicBySlug("article")).thenReturn(detail());
        mockMvc.perform(get("/articles/latest")).andExpect(status().isOk()); mockMvc.perform(get("/articles")).andExpect(status().isOk()); mockMvc.perform(get("/articles/years")).andExpect(status().isOk()); mockMvc.perform(get("/articles/article")).andExpect(status().isOk());
        mockMvc.perform(get("/admin/articles")).andExpect(status().isUnauthorized()); mockMvc.perform(post("/admin/articles").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized()); mockMvc.perform(patch("/admin/articles/7").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized()); mockMvc.perform(delete("/admin/articles/7")).andExpect(status().isUnauthorized());
    }

    @Test void onlyAdminWithProjectManageCanReadAndMutateArticles() throws Exception {
        when(articleService.listAdmin(0, 20)).thenReturn(new PublicPageResponse<>(List.of(admin()), 0, 20, 1, 1)); when(articleService.create(any(CreateLabArticleRequest.class))).thenReturn(admin()); when(articleService.update(eq(7L), any(UpdateLabArticleRequest.class))).thenReturn(admin());
        mockMvc.perform(get("/admin/articles").with(adminUser())).andExpect(status().isOk()); mockMvc.perform(post("/admin/articles").with(adminUser()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated()); mockMvc.perform(patch("/admin/articles/7").with(adminUser()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Changed\"}")).andExpect(status().isOk()); mockMvc.perform(delete("/admin/articles/7").with(adminUser())).andExpect(status().isNoContent());
        mockMvc.perform(get("/admin/articles").with(user("member@test").authorities(() -> "ROLE_MEMBER", () -> "PROJECT_MANAGE"))).andExpect(status().isForbidden()); mockMvc.perform(get("/admin/articles").with(user("leader@test").authorities(() -> "ROLE_LEADER", () -> "PROJECT_MANAGE"))).andExpect(status().isForbidden());
        verify(articleService).delete(7L);
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor adminUser() { return user("admin@smartlab.test").authorities(() -> "ROLE_ADMIN", () -> "PROJECT_MANAGE"); }
    private static PublicLabArticleSummaryResponse summary() { return new PublicLabArticleSummaryResponse(7L, "Article", "article", "Excerpt", Instant.now()); }
    private static PublicLabArticleDetailResponse detail() { return new PublicLabArticleDetailResponse(7L, "Article", "article", "Excerpt", Map.of("type", "doc"), Instant.now()); }
    private static AdminLabArticleResponse admin() { return new AdminLabArticleResponse(7L, "Article", "article", "Excerpt", Map.of("type", "doc"), LabArticleStatus.DRAFT, null, Instant.now(), Instant.now()); }
}
