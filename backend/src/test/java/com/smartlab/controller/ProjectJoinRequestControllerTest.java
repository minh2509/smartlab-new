package com.smartlab.controller;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
import com.smartlab.dto.request.CreateProjectJoinRequest;
import com.smartlab.dto.request.ReviewProjectJoinRequest;
import com.smartlab.dto.response.ProjectJoinRequestResponse;
import com.smartlab.dto.response.ProjectMembershipHistoryResponse;
import com.smartlab.enums.ProjectJoinRequestStatus;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import com.smartlab.enums.ProjectStatus;
import com.smartlab.filter.JwtRequestFilter;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.ProjectJoinRequestService;
import com.smartlab.service.ProjectMemberService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ProjectJoinRequestController.class, ProjectMembershipController.class})
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, JwtRequestFilter.class})
class ProjectJoinRequestControllerTest {
    private static final String MEMBER_EMAIL = "member@smartlab.test";
    private static final String LEADER_EMAIL = "leader@smartlab.test";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ProjectJoinRequestService joinRequestService;
    @MockitoBean private ProjectMemberService projectMemberService;
    @MockitoBean private AppUserDetailService appUserDetailService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserSessionService userSessionService;

    @Test
    void ownMembershipHistoryRequiresAuthenticationDespitePublicProjectGetMatcher() throws Exception {
        mockMvc.perform(get("/projects/memberships/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(projectMemberService);
    }

    @Test
    void authenticatedUserCanReadOnlyTheirOwnMembershipHistory() throws Exception {
        when(projectMemberService.listMine(MEMBER_EMAIL)).thenReturn(List.of(
                ProjectMembershipHistoryResponse.builder()
                        .projectId(7L)
                        .projectCode("SL-AI")
                        .projectName("Smart Lab AI")
                        .projectStatus(ProjectStatus.IN_PROGRESS)
                        .projectRole(ProjectRole.MEMBER)
                        .status(ProjectMemberStatus.REMOVED)
                        .joinedAt(Instant.parse("2026-01-01T00:00:00Z"))
                        .removedAt(Instant.parse("2026-08-01T00:00:00Z"))
                        .build()
        ));

        mockMvc.perform(get("/projects/memberships/me").with(user(MEMBER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").value(7))
                .andExpect(jsonPath("$[0].status").value("REMOVED"));

        verify(projectMemberService).listMine(MEMBER_EMAIL);
    }

    @Test
    void authenticatedUserCanCreateAndReadTheirLatestRequest() throws Exception {
        ProjectJoinRequestResponse response = response(ProjectJoinRequestStatus.PENDING);
        when(joinRequestService.create(eq(7L), any(CreateProjectJoinRequest.class), eq(MEMBER_EMAIL)))
                .thenReturn(response);
        when(joinRequestService.getLatestMine(7L, MEMBER_EMAIL)).thenReturn(Optional.of(response));

        mockMvc.perform(post("/projects/7/join-requests")
                        .with(user(MEMBER_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Please add me\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(get("/projects/7/join-requests/me").with(user(MEMBER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requesterUserId").value("member-user"));
    }

    @Test
    void latestRequestReturnsNoContentWhenUserHasNeverRequested() throws Exception {
        when(joinRequestService.getLatestMine(7L, MEMBER_EMAIL)).thenReturn(Optional.empty());

        mockMvc.perform(get("/projects/7/join-requests/me").with(user(MEMBER_EMAIL)))
                .andExpect(status().isNoContent());
    }

    @Test
    void authenticatedProjectManagerRequestReachesReviewService() throws Exception {
        when(joinRequestService.review(
                eq(7L), eq(41L), any(ReviewProjectJoinRequest.class), eq(LEADER_EMAIL)
        )).thenReturn(response(ProjectJoinRequestStatus.APPROVED));

        mockMvc.perform(patch("/projects/7/join-requests/41")
                        .with(user(LEADER_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(joinRequestService).review(
                eq(7L), eq(41L), any(ReviewProjectJoinRequest.class), eq(LEADER_EMAIL)
        );
    }

    private static ProjectJoinRequestResponse response(ProjectJoinRequestStatus status) {
        return ProjectJoinRequestResponse.builder()
                .id(41L)
                .projectId(7L)
                .projectCode("SL-AI")
                .projectName("Smart Lab AI")
                .requesterUserId("member-user")
                .requesterName("Member")
                .requesterEmail(MEMBER_EMAIL)
                .status(status)
                .createdAt(Instant.parse("2026-08-17T01:00:00Z"))
                .updatedAt(Instant.parse("2026-08-17T01:00:00Z"))
                .build();
    }
}
