package com.smartlab.controller;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
import com.smartlab.dto.request.CreateLabNewsArticleRequest;
import com.smartlab.dto.request.UpdateLabNewsArticleRequest;
import com.smartlab.dto.response.LabNewsArticleResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.PublicNewsSort;
import com.smartlab.filter.JwtRequestFilter;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.LabNewsArticleService;
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

@WebMvcTest(LabNewsArticleController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, JwtRequestFilter.class})
class LabNewsArticleControllerSecurityTest {
    private static final String BODY = "{\"title\":\"Article\",\"sourceName\":\"FPT Education\",\"sourceUrl\":\"https://example.test/article\",\"publishedAt\":\"2026-08-20T00:00:00Z\"}";
    @Autowired private MockMvc mockMvc;
    @MockitoBean private LabNewsArticleService articleService;
    @MockitoBean private AppUserDetailService appUserDetailService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserSessionService userSessionService;

    @Test void anonymousCanReadNewsButCannotMutate() throws Exception {
        when(articleService.listPublic(3)).thenReturn(List.of(response()));
        when(articleService.listPublicArchive(null, null, null, PublicNewsSort.LATEST, 0, 12)).thenReturn(new PublicPageResponse<>(List.of(response()), 0, 12, 1, 1));
        when(articleService.listPublicSources()).thenReturn(List.of("Source"));
        when(articleService.listPublicYears()).thenReturn(List.of(2026));
        mockMvc.perform(get("/news")).andExpect(status().isOk());
        mockMvc.perform(get("/news/archive")).andExpect(status().isOk());
        mockMvc.perform(get("/news/archive/sources")).andExpect(status().isOk());
        mockMvc.perform(get("/news/archive/years")).andExpect(status().isOk());
        mockMvc.perform(post("/admin/news").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/admin/news/7").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/admin/news/7")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/news")).andExpect(status().isUnauthorized());
        verify(articleService).listPublic(3);
        verify(articleService).listPublicArchive(null, null, null, PublicNewsSort.LATEST, 0, 12);
    }

    @Test void onlyAdminWithApprovedProjectManageAuthorityCanMutate() throws Exception {
        when(articleService.create(any(CreateLabNewsArticleRequest.class))).thenReturn(response());
        when(articleService.update(eq(7L), any(UpdateLabNewsArticleRequest.class))).thenReturn(response());
        mockMvc.perform(post("/admin/news").with(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());
        mockMvc.perform(patch("/admin/news/7").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mockMvc.perform(delete("/admin/news/7").with(admin())).andExpect(status().isNoContent());
        mockMvc.perform(post("/admin/news").with(user("member@test").authorities(() -> "ROLE_MEMBER", () -> "PROJECT_MANAGE"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/news").with(user("leader@test").authorities(() -> "ROLE_LEADER", () -> "PROJECT_MANAGE"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verify(articleService).create(any(CreateLabNewsArticleRequest.class)); verify(articleService).update(eq(7L), any(UpdateLabNewsArticleRequest.class));
        verify(articleService).delete(7L);
    }

    @Test void onlyAdminWithApprovedProjectManageAuthorityCanReadAdminNews() throws Exception {
        when(articleService.listAdmin(0, 20)).thenReturn(new PublicPageResponse<>(List.of(response()), 0, 20, 1, 1));
        mockMvc.perform(get("/admin/news").with(admin())).andExpect(status().isOk());
        mockMvc.perform(get("/admin/news").with(user("member@test").authorities(() -> "ROLE_MEMBER", () -> "PROJECT_MANAGE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/news").with(user("leader@test").authorities(() -> "ROLE_LEADER", () -> "PROJECT_MANAGE")))
                .andExpect(status().isForbidden());
        verify(articleService).listAdmin(0, 20);
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
        return user("admin@smartlab.test").authorities(() -> "ROLE_ADMIN", () -> "PROJECT_MANAGE");
    }

    private static LabNewsArticleResponse response() {
        return new LabNewsArticleResponse(7L, "Article", null, "FPT Education", "https://example.test/article",
                Instant.now(), true, Instant.now(), Instant.now(), null);
    }
}
