package com.smartlab.controller;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
import com.smartlab.dto.request.CreateLabAchievementRequest;
import com.smartlab.dto.request.UpdateLabAchievementRequest;
import com.smartlab.dto.response.AdminLabAchievementResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.AchievementType;
import com.smartlab.filter.JwtRequestFilter;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.LabAchievementService;
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
import java.time.Year;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LabAchievementController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, JwtRequestFilter.class})
class LabAchievementControllerSecurityTest {
    private static final String BODY = "{\"title\":\"Award\",\"achievementType\":\"AWARD\",\"achievementYear\":2026}";
    @Autowired private MockMvc mockMvc;
    @MockitoBean private LabAchievementService achievementService;
    @MockitoBean private AppUserDetailService appUserDetailService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserSessionService userSessionService;

    @Test void anonymousCanReadAchievementsButCannotMutateAndObsoleteRouteIsNotPublic() throws Exception {
        mockMvc.perform(get("/achievements")).andExpect(status().isOk());
        mockMvc.perform(get("/achievements/years")).andExpect(status().isOk());
        mockMvc.perform(get("/publications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/achievements")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/admin/achievements").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/admin/achievements/7").contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/admin/achievements/7")).andExpect(status().isUnauthorized());
        verify(achievementService).listPublic(null, 0, 10); verify(achievementService).listPublicYears();
    }

    @Test void authorizedAdminCanMutateWhileOtherPrincipalsCannot() throws Exception {
        when(achievementService.create(any(CreateLabAchievementRequest.class))).thenReturn(response());
        when(achievementService.update(eq(7L), any(UpdateLabAchievementRequest.class))).thenReturn(response());
        when(achievementService.listAdmin(null, null, null, null, 0, 20)).thenReturn(new PublicPageResponse<>(List.of(response()), 0, 20, 1, 1));
        mockMvc.perform(get("/admin/achievements").with(admin())).andExpect(status().isOk());
        mockMvc.perform(post("/admin/achievements").with(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());
        mockMvc.perform(patch("/admin/achievements/7").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isOk());
        mockMvc.perform(delete("/admin/achievements/7").with(admin())).andExpect(status().isNoContent());
        mockMvc.perform(post("/admin/achievements").with(user("member@test").authorities(() -> "ROLE_MEMBER", () -> "PROJECT_MANAGE"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/achievements").with(user("member@test").authorities(() -> "ROLE_MEMBER", () -> "PROJECT_MANAGE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/achievements").with(user("admin-no-permission@test").authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isForbidden());
        verify(achievementService).create(any(CreateLabAchievementRequest.class)); verify(achievementService).update(eq(7L), any(UpdateLabAchievementRequest.class)); verify(achievementService).delete(7L);
    }

    @Test void adminListRejectsInvalidPaginationYearAndQuery() throws Exception {
        mockMvc.perform(get("/admin/achievements").with(admin()).queryParam("page", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/admin/achievements").with(admin()).queryParam("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/admin/achievements").with(admin()).queryParam("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/admin/achievements").with(admin()).queryParam("year", "2022")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/admin/achievements").with(admin()).queryParam("year", String.valueOf(Year.now().getValue() + 1))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/admin/achievements").with(admin()).queryParam("q", "x".repeat(201))).andExpect(status().isBadRequest());
        verifyNoInteractions(achievementService);
    }

    @Test void adminListAcceptsAchievementDomainBoundaryYears() throws Exception {
        when(achievementService.listAdmin(any(), eq(null), eq(null), eq(null), eq(0), eq(20)))
                .thenReturn(new PublicPageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/admin/achievements").with(admin()).queryParam("year", "2023")).andExpect(status().isOk());
        mockMvc.perform(get("/admin/achievements").with(admin())
                .queryParam("year", String.valueOf(Year.now().getValue()))).andExpect(status().isOk());

        verify(achievementService).listAdmin(2023, null, null, null, 0, 20);
        verify(achievementService).listAdmin(Year.now().getValue(), null, null, null, 0, 20);
    }

    @Test void attachmentRoutesRequireExactAdminAuthoritiesAndPublicDownloadReachesService() throws Exception {
        mockMvc.perform(get("/admin/achievements/7/files")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/achievements/7/files").with(user("member@test").authorities(() -> "ROLE_MEMBER", () -> "PROJECT_MANAGE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/achievements/7/files").with(user("admin-no-project-manage@test").authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/achievements/7/files").with(admin())).andExpect(status().isOk());

        mockMvc.perform(multipart("/admin/achievements/7/files").file("file", new byte[]{1}).with(admin()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/admin/achievements/7/files").file("file", new byte[]{1})
                .with(user("admin-file-only@test").authorities(() -> "ROLE_ADMIN", () -> "FILE_UPLOAD")))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/admin/achievements/7/files").file("file", new byte[]{1})
                .with(user("member-both@test").authorities(() -> "ROLE_MEMBER", () -> "PROJECT_MANAGE", () -> "FILE_UPLOAD")))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/admin/achievements/7/files").file("file", new byte[]{1})
                .with(user("admin@test").authorities(() -> "ROLE_ADMIN", () -> "PROJECT_MANAGE", () -> "FILE_UPLOAD")))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/admin/achievements/7/files/8").with(user("admin-no-project-manage@test").authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/admin/achievements/7/files/8").with(user("member@test").authorities(() -> "ROLE_MEMBER", () -> "PROJECT_MANAGE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/admin/achievements/7/files/8").with(admin())).andExpect(status().isNoContent());
        when(achievementService.downloadPublicFile(7L, 8L)).thenReturn(new LabAchievementService.FileDownload(new byte[]{1}, "application/pdf", "evidence.pdf"));
        mockMvc.perform(get("/achievements/7/files/8")).andExpect(status().isOk());
        verify(achievementService).downloadPublicFile(7L, 8L);
    }

    @Test void adminListNormalizesBlankQueryBeforeValidationAndForwarding() throws Exception {
        when(achievementService.listAdmin(null, null, null, null, 0, 20)).thenReturn(new PublicPageResponse<>(List.of(), 0, 20, 0, 0));
        mockMvc.perform(get("/admin/achievements").with(admin()).queryParam("q", " ".repeat(201))).andExpect(status().isOk());
        verify(achievementService).listAdmin(null, null, null, null, 0, 20);
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
        return user("admin@smartlab.test").authorities(() -> "ROLE_ADMIN", () -> "PROJECT_MANAGE");
    }

    private static AdminLabAchievementResponse response() {
        return new AdminLabAchievementResponse(7L, "Award", null, AchievementType.AWARD, 2026, null, null, 7L,
                true, Instant.now(), Instant.now());
    }
}
