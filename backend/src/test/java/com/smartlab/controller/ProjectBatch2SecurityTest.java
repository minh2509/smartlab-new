package com.smartlab.controller;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
import com.smartlab.dto.request.AddProjectMemberRequest;
import com.smartlab.dto.request.ReplaceProjectResearchFieldsRequest;
import com.smartlab.filter.JwtRequestFilter;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.ProjectMemberService;
import com.smartlab.service.ProjectResearchFieldService;
import com.smartlab.service.UserSessionService;
import com.smartlab.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ProjectMemberController.class, ProjectResearchFieldController.class})
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, JwtRequestFilter.class})
class ProjectBatch2SecurityTest {
    private static final String EMAIL = "member@smartlab.test";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectMemberService projectMemberService;
    @MockitoBean
    private ProjectResearchFieldService projectResearchFieldService;
    @MockitoBean
    private AppUserDetailService appUserDetailService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserSessionService userSessionService;

    @Test
    void anonymousCanFilterPublicProjectsButCannotReachNestedManagementEndpoints() throws Exception {
        when(projectResearchFieldService.filterVisibleProjects(3L, "anonymousUser")).thenReturn(List.of());

        mockMvc.perform(get("/projects").queryParam("researchFieldId", "3"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/projects/7/members"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/projects/7/member-candidates"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/projects/7/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"member-user\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/projects/7/members/member-user"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/projects/7/research-fields"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/projects/7/research-fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldIds\":[]}"))
                .andExpect(status().isUnauthorized());

        verify(projectResearchFieldService).filterVisibleProjects(3L, "anonymousUser");
        verifyNoInteractions(projectMemberService);
    }

    @Test
    void authenticatedAccountReachesServicesForObjectLevelAuthorization() throws Exception {
        when(projectMemberService.list(7L, null, EMAIL)).thenReturn(List.of());
        when(projectMemberService.add(eq(7L), any(AddProjectMemberRequest.class), eq(EMAIL)))
                .thenReturn(null);
        when(projectResearchFieldService.list(7L, EMAIL)).thenReturn(List.of());
        when(projectResearchFieldService.replace(
                eq(7L), any(ReplaceProjectResearchFieldsRequest.class), eq(EMAIL)
        )).thenReturn(List.of());

        mockMvc.perform(get("/projects/7/members").with(user(EMAIL)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/projects/7/members")
                        .with(user(EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"member-user\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/projects/7/research-fields").with(user(EMAIL)))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/projects/7/research-fields")
                        .with(user(EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldIds\":[]}"))
                .andExpect(status().isOk());

        verify(projectMemberService).list(7L, null, EMAIL);
        verify(projectMemberService).add(eq(7L), any(AddProjectMemberRequest.class), eq(EMAIL));
        verify(projectResearchFieldService).list(7L, EMAIL);
        verify(projectResearchFieldService).replace(
                eq(7L), any(ReplaceProjectResearchFieldsRequest.class), eq(EMAIL)
        );
    }
}
