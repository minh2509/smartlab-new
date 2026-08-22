package com.smartlab.controller;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
import com.smartlab.dto.request.CreateResearchPublicationRequest;
import com.smartlab.dto.request.UpdateResearchPublicationRequest;
import com.smartlab.dto.response.ResearchPublicationResponse;
import com.smartlab.enums.PublicationType;
import com.smartlab.filter.JwtRequestFilter;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.ResearchPublicationService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResearchPublicationController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, JwtRequestFilter.class})
class ResearchPublicationControllerSecurityTest {
    private static final String ADMIN_EMAIL = "admin@smartlab.test";
    private static final String CREATE_BODY = """
            {"title":"Publication","authors":"A. Author","publicationType":"JOURNAL_ARTICLE","venue":"Venue","publicationYear":2026}
            """;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ResearchPublicationService publicationService;
    @MockitoBean private AppUserDetailService appUserDetailService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserSessionService userSessionService;

    @Test
    void anonymousCanReadPublicPublicationsButCannotMutateThem() throws Exception {
        mockMvc.perform(get("/publications")).andExpect(status().isOk());
        mockMvc.perform(get("/publications/years")).andExpect(status().isOk());
        mockMvc.perform(post("/admin/publications").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/admin/publications/7").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Updated\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/admin/publications/7")).andExpect(status().isUnauthorized());

        verify(publicationService).listPublic(null, 0, 8);
        verify(publicationService).listPublicYears();
    }

    @Test
    void adminWithProjectManageCanCreateUpdateAndDeletePublication() throws Exception {
        when(publicationService.create(any(CreateResearchPublicationRequest.class))).thenReturn(response());
        when(publicationService.update(eq(7L), any(UpdateResearchPublicationRequest.class))).thenReturn(response());

        mockMvc.perform(post("/admin/publications").with(admin()).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/admin/publications/7").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/admin/publications/7").with(admin())).andExpect(status().isNoContent());

        verify(publicationService).create(any(CreateResearchPublicationRequest.class));
        verify(publicationService).update(eq(7L), any(UpdateResearchPublicationRequest.class));
        verify(publicationService).delete(7L);
    }

    @Test
    void memberOrAdminWithoutRequiredAuthorityCannotMutatePublication() throws Exception {
        mockMvc.perform(post("/admin/publications")
                        .with(user("member@smartlab.test").authorities(() -> "ROLE_MEMBER", () -> "PROJECT_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/admin/publications/7")
                        .with(user(ADMIN_EMAIL).authorities(() -> "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Updated\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/admin/publications/7")
                        .with(user("member@smartlab.test").authorities(() -> "ROLE_MEMBER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(publicationService);
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
        return user(ADMIN_EMAIL).authorities(() -> "ROLE_ADMIN", () -> "PROJECT_MANAGE");
    }

    private static ResearchPublicationResponse response() {
        return ResearchPublicationResponse.builder()
                .id(7L).title("Publication").authors("A. Author")
                .publicationType(PublicationType.JOURNAL_ARTICLE).venue("Venue").publicationYear(2026)
                .isPublic(true).createdAt(Instant.parse("2026-08-20T08:00:00Z"))
                .updatedAt(Instant.parse("2026-08-20T08:00:00Z")).build();
    }
}
