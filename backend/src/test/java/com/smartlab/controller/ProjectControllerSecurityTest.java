package com.smartlab.controller;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
import com.smartlab.dto.request.ChangeProjectLeaderRequest;
import com.smartlab.dto.request.ChangeProjectLeadersRequest;
import com.smartlab.dto.request.CreateProjectRequest;
import com.smartlab.dto.request.UpdateProjectRequest;
import com.smartlab.dto.request.UpdateProjectLeadershipRequest;
import com.smartlab.dto.response.LeaderCandidateResponse;
import com.smartlab.dto.response.ProjectLeaderResponse;
import com.smartlab.dto.response.ProjectResponse;
import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import com.smartlab.filter.JwtRequestFilter;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.ProjectService;
import com.smartlab.service.UserSessionService;
import com.smartlab.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, JwtRequestFilter.class})
class ProjectControllerSecurityTest {
    private static final String ADMIN_EMAIL = "admin@smartlab.test";
    private static final String CREATE_BODY = """
            {
              "code": "SL-AI",
              "name": "Smart Lab AI"
            }
            """;
    private static final String PRIMARY_LEADER_BODY = """
            {"leaderUserId":"new-leader"}
            """;
    private static final String LEADERS_BODY = """
            {"leaderUserIds":["leader-user", "co-leader"]}
            """;
    private static final String UPDATE_BODY = """
            {"name":"Updated project"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private AppUserDetailService appUserDetailService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserSessionService userSessionService;

    @Test
    void anonymousCannotCreateProject() throws Exception {
        mockMvc.perform(createProjectRequest())
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(projectService);
    }

    @Test
    void adminWithoutProjectManageCannotCreateProject() throws Exception {
        mockMvc.perform(createProjectRequest()
                        .with(user(ADMIN_EMAIL).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(projectService);
    }

    @Test
    void memberWithProjectManageCannotCreateProject() throws Exception {
        mockMvc.perform(createProjectRequest()
                        .with(user("member@smartlab.test").authorities(
                                () -> "ROLE_MEMBER",
                                () -> "PROJECT_MANAGE"
                        )))
                .andExpect(status().isForbidden());

        verifyNoInteractions(projectService);
    }

    @Test
    void adminWithProjectManageCanCreateProject() throws Exception {
        when(projectService.create(any(CreateProjectRequest.class), eq(ADMIN_EMAIL)))
                .thenReturn(projectResponse());

        mockMvc.perform(createProjectRequest()
                        .with(user(ADMIN_EMAIL).authorities(
                                () -> "ROLE_ADMIN",
                                () -> "PROJECT_MANAGE"
                )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.primaryLeader.userId").value("leader-user"))
                .andExpect(jsonPath("$.leaders[0].userId").value("leader-user"));

        verify(projectService).create(any(CreateProjectRequest.class), eq(ADMIN_EMAIL));
    }

    @Test
    void anonymousCannotSearchLeaderCandidates() throws Exception {
        mockMvc.perform(get("/projects/leader-candidates"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(projectService);
    }

    @Test
    void memberWithProjectManageCannotSearchLeaderCandidates() throws Exception {
        mockMvc.perform(get("/projects/leader-candidates")
                        .with(user("member@smartlab.test").authorities(
                                () -> "ROLE_MEMBER",
                                () -> "PROJECT_MANAGE"
                        )))
                .andExpect(status().isForbidden());

        verifyNoInteractions(projectService);
    }

    @Test
    void adminWithoutProjectManageCannotSearchLeaderCandidates() throws Exception {
        mockMvc.perform(get("/projects/leader-candidates")
                        .with(user(ADMIN_EMAIL).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(projectService);
    }

    @Test
    void adminWithProjectManageCanSearchLeaderCandidates() throws Exception {
        when(projectService.findLeaderCandidates("leader", ADMIN_EMAIL)).thenReturn(java.util.List.of(
                LeaderCandidateResponse.builder()
                        .userId("leader-user")
                        .name("Leader")
                        .email("leader@smartlab.test")
                        .build()
        ));

        mockMvc.perform(get("/projects/leader-candidates")
                        .queryParam("query", "leader")
                        .with(user(ADMIN_EMAIL).authorities(
                                () -> "ROLE_ADMIN",
                                () -> "PROJECT_MANAGE"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("leader@smartlab.test"));

        verify(projectService).findLeaderCandidates("leader", ADMIN_EMAIL);
    }

    @Test
    void anonymousCannotUpdateProject() throws Exception {
        mockMvc.perform(updateProjectRequest())
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(projectService);
    }

    @Test
    void authenticatedMemberWithoutGlobalProjectManageCanReachUpdateService() throws Exception {
        String memberEmail = "member@smartlab.test";
        when(projectService.update(any(Long.class), any(UpdateProjectRequest.class), eq(memberEmail)))
                .thenReturn(projectResponse());

        mockMvc.perform(updateProjectRequest()
                        .with(user(memberEmail).authorities(() -> "ROLE_MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        verify(projectService).update(eq(7L), any(UpdateProjectRequest.class), eq(memberEmail));
    }

    @Test
    void memberWithProjectManageCannotChangePrimaryLeader() throws Exception {
        mockMvc.perform(changePrimaryLeaderRequest()
                        .with(user("member@smartlab.test").authorities(
                                () -> "ROLE_MEMBER",
                                () -> "PROJECT_MANAGE"
                        )))
                .andExpect(status().isForbidden());

        verifyNoInteractions(projectService);
    }

    @Test
    void adminWithoutProjectManageCannotChangePrimaryLeader() throws Exception {
        mockMvc.perform(changePrimaryLeaderRequest()
                        .with(user(ADMIN_EMAIL).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(projectService);
    }

    @Test
    void adminWithProjectManageCanChangePrimaryLeader() throws Exception {
        when(projectService.changeLeader(any(Long.class), any(ChangeProjectLeaderRequest.class), eq(ADMIN_EMAIL)))
                .thenReturn(projectResponse());

        mockMvc.perform(changePrimaryLeaderRequest()
                        .with(user(ADMIN_EMAIL).authorities(
                                () -> "ROLE_ADMIN",
                                () -> "PROJECT_MANAGE"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryLeader.userId").value("leader-user"));

        verify(projectService).changeLeader(eq(7L), any(ChangeProjectLeaderRequest.class), eq(ADMIN_EMAIL));
    }

    @Test
    void memberWithProjectManageCannotReplaceLeaderSet() throws Exception {
        mockMvc.perform(replaceLeadersRequest()
                        .with(user("member@smartlab.test").authorities(
                                () -> "ROLE_MEMBER",
                                () -> "PROJECT_MANAGE"
                        )))
                .andExpect(status().isForbidden());

        verifyNoInteractions(projectService);
    }

    @Test
    void adminWithoutProjectManageCannotReplaceLeaderSet() throws Exception {
        mockMvc.perform(replaceLeadersRequest()
                        .with(user(ADMIN_EMAIL).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(projectService);
    }

    @Test
    void adminWithProjectManageCanReplaceLeaderSet() throws Exception {
        when(projectService.replaceLeaders(any(Long.class), any(ChangeProjectLeadersRequest.class), eq(ADMIN_EMAIL)))
                .thenReturn(projectResponse());

        mockMvc.perform(replaceLeadersRequest()
                        .with(user(ADMIN_EMAIL).authorities(
                                () -> "ROLE_ADMIN",
                                () -> "PROJECT_MANAGE"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaders[0].userId").value("leader-user"));

        verify(projectService).replaceLeaders(eq(7L), any(ChangeProjectLeadersRequest.class), eq(ADMIN_EMAIL));
    }

    @Test
    void memberWithProjectManageCannotUpdateCompleteLeadership() throws Exception {
        mockMvc.perform(updateLeadershipRequest()
                        .with(user("member@smartlab.test").authorities(
                                () -> "ROLE_MEMBER",
                                () -> "PROJECT_MANAGE"
                        )))
                .andExpect(status().isForbidden());

        verifyNoInteractions(projectService);
    }

    @Test
    void anonymousCannotUpdateCompleteLeadership() throws Exception {
        mockMvc.perform(updateLeadershipRequest())
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(projectService);
    }

    @Test
    void adminWithoutProjectManageCannotUpdateCompleteLeadership() throws Exception {
        mockMvc.perform(updateLeadershipRequest()
                        .with(user(ADMIN_EMAIL).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(projectService);
    }

    @Test
    void adminWithProjectManageCanUpdateCompleteLeadership() throws Exception {
        when(projectService.updateLeadership(
                any(Long.class),
                any(UpdateProjectLeadershipRequest.class),
                eq(ADMIN_EMAIL)
        )).thenReturn(projectResponse());

        mockMvc.perform(updateLeadershipRequest()
                        .with(user(ADMIN_EMAIL).authorities(
                                () -> "ROLE_ADMIN",
                                () -> "PROJECT_MANAGE"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryLeader.userId").value("leader-user"));

        verify(projectService).updateLeadership(
                eq(7L),
                any(UpdateProjectLeadershipRequest.class),
                eq(ADMIN_EMAIL)
        );
    }

    @Test
    void memberWithProjectManageCannotDeleteProject() throws Exception {
        mockMvc.perform(delete("/projects/7")
                        .with(user("member@smartlab.test").authorities(
                                () -> "ROLE_MEMBER",
                                () -> "PROJECT_MANAGE"
                        )))
                .andExpect(status().isForbidden());

        verifyNoInteractions(projectService);
    }

    @Test
    void adminWithProjectManageCanDeleteProject() throws Exception {
        mockMvc.perform(delete("/projects/7")
                        .with(user(ADMIN_EMAIL).authorities(
                                () -> "ROLE_ADMIN",
                                () -> "PROJECT_MANAGE"
                        )))
                .andExpect(status().isNoContent());

        verify(projectService).delete(7L, ADMIN_EMAIL);
    }

    @Test
    void anonymousCanReadProjectDetail() throws Exception {
        when(projectService.get(eq(7L), nullable(String.class))).thenReturn(projectResponse());

        mockMvc.perform(get("/projects/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void anonymousCannotAccessNestedProjectRoutes() throws Exception {
        mockMvc.perform(get("/projects/7/documents"))
                .andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder createProjectRequest() {
        return post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY);
    }

    private static MockHttpServletRequestBuilder updateProjectRequest() {
        return patch("/projects/7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY);
    }

    private static MockHttpServletRequestBuilder changePrimaryLeaderRequest() {
        return patch("/projects/7/leader")
                .contentType(MediaType.APPLICATION_JSON)
                .content(PRIMARY_LEADER_BODY);
    }

    private static MockHttpServletRequestBuilder replaceLeadersRequest() {
        return put("/projects/7/leaders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LEADERS_BODY);
    }

    private static MockHttpServletRequestBuilder updateLeadershipRequest() {
        return put("/projects/7/leadership")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "primaryLeaderUserId":"leader-user",
                          "leaderUserIds":["leader-user", "co-leader"]
                        }
                        """);
    }

    private static ProjectResponse projectResponse() {
        ProjectLeaderResponse leader = ProjectLeaderResponse.builder()
                .userId("leader-user")
                .name("Leader")
                .build();

        return ProjectResponse.builder()
                .id(7L)
                .code("SL-AI")
                .name("Smart Lab AI")
                .projectType(ProjectType.RESEARCH)
                .status(ProjectStatus.PROPOSED)
                .isPublic(false)
                .isFeatured(false)
                .primaryLeader(leader)
                .leaders(java.util.List.of(leader))
                .build();
    }
}
