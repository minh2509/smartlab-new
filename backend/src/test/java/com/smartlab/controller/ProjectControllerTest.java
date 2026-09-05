package com.smartlab.controller;

import com.smartlab.dto.request.ChangeProjectLeaderRequest;
import com.smartlab.dto.request.ChangeProjectLeadersRequest;
import com.smartlab.dto.request.CreateProjectRequest;
import com.smartlab.dto.request.UpdateProjectRequest;
import com.smartlab.dto.request.UpdateProjectLeadershipRequest;
import com.smartlab.dto.response.LeaderCandidateResponse;
import com.smartlab.dto.response.ProjectLeaderResponse;
import com.smartlab.dto.response.ProjectResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import com.smartlab.enums.PublicProjectStatus;
import com.smartlab.service.ProjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.CurrentSecurityContextArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    private static final String CURRENT_EMAIL = "admin@smartlab.test";

    @Mock
    private ProjectService projectService;

    @InjectMocks
    private ProjectController projectController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(CURRENT_EMAIL, null)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(projectController)
                .setCustomArgumentResolvers(new CurrentSecurityContextArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsVisibleProjects() throws Exception {
        when(projectService.list(CURRENT_EMAIL)).thenReturn(List.of(response(7L, "leader-user")));

        mockMvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].code").value("SL-AI"))
                .andExpect(jsonPath("$[0].primaryLeader.userId").value("leader-user"))
                .andExpect(jsonPath("$[0].leaders[0].userId").value("leader-user"));
    }

    @Test
    void listsPublicProjectsWithServerSideFiltersAndPagination() throws Exception {
        when(projectService.listPublic(
                1, 12, "robot", 3L, "ai", ProjectType.RESEARCH, PublicProjectStatus.RECRUITING
        )).thenReturn(new PublicPageResponse<>(List.of(response(7L, "leader-user")), 1, 12, 13, 2));

        mockMvc.perform(get("/projects/public")
                        .queryParam("page", "1")
                        .queryParam("size", "12")
                        .queryParam("q", "robot")
                        .queryParam("researchFieldId", "3")
                        .queryParam("field", "ai")
                        .queryParam("projectType", "RESEARCH")
                        .queryParam("status", "RECRUITING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(7))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.totalElements").value(13))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(projectService).listPublic(1, 12, "robot", 3L, "ai", ProjectType.RESEARCH, PublicProjectStatus.RECRUITING);
    }

    @Test
    void searchesLeaderCandidatesByNameOrEmail() throws Exception {
        when(projectService.findLeaderCandidates("nguyen", CURRENT_EMAIL)).thenReturn(List.of(
                LeaderCandidateResponse.builder()
                        .userId("leader-user")
                        .name("Nguyen Van A")
                        .email("leader@smartlab.test")
                        .build()
        ));

        mockMvc.perform(get("/projects/leader-candidates").queryParam("query", "nguyen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("leader-user"))
                .andExpect(jsonPath("$[0].name").value("Nguyen Van A"))
                .andExpect(jsonPath("$[0].email").value("leader@smartlab.test"));

        verify(projectService).findLeaderCandidates("nguyen", CURRENT_EMAIL);
    }

    @Test
    void createsProjectWithCreatedStatusAndDelegatesAuthenticatedEmail() throws Exception {
        when(projectService.create(any(CreateProjectRequest.class), any(String.class)))
                .thenReturn(response(7L, "leader-user", "co-leader"));

        mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "SL-AI",
                                  "name": "Smart Lab AI",
                                  "leaderUserId": "leader-user",
                                  "additionalLeaderUserIds": ["co-leader"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.projectType").value("RESEARCH"))
                .andExpect(jsonPath("$.status").value("PROPOSED"))
                .andExpect(jsonPath("$.leaders[1].userId").value("co-leader"));

        ArgumentCaptor<CreateProjectRequest> request = ArgumentCaptor.forClass(CreateProjectRequest.class);
        verify(projectService).create(request.capture(), org.mockito.ArgumentMatchers.eq(CURRENT_EMAIL));
        assertThat(request.getValue())
                .extracting(CreateProjectRequest::getCode,
                        CreateProjectRequest::getName,
                        CreateProjectRequest::getLeaderUserId)
                .containsExactly("SL-AI", "Smart Lab AI", "leader-user");
        assertThat(request.getValue().getAdditionalLeaderUserIds()).containsExactly("co-leader");
    }

    @Test
    void createsProjectWithoutLeaders() throws Exception {
        when(projectService.create(any(CreateProjectRequest.class), any(String.class)))
                .thenReturn(response(7L, null));

        mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "SL-NO-LEADER",
                                  "name": "Project without a leader"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.primaryLeader").doesNotExist())
                .andExpect(jsonPath("$.leaders").isEmpty());

        ArgumentCaptor<CreateProjectRequest> request = ArgumentCaptor.forClass(CreateProjectRequest.class);
        verify(projectService).create(request.capture(), org.mockito.ArgumentMatchers.eq(CURRENT_EMAIL));
        assertThat(request.getValue().getLeaderUserId()).isNull();
        assertThat(request.getValue().getAdditionalLeaderUserIds()).isNull();
    }

    @Test
    void rejectsInvalidCreateRequestBeforeCallingService() throws Exception {
        mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"",
                                  "name":"Smart Lab AI",
                                  "leaderUserId":"",
                                  "additionalLeaderUserIds":[" "]
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(projectService, never()).create(any(), any());
    }

    @Test
    void patchesProjectDetails() throws Exception {
        when(projectService.update(any(Long.class), any(UpdateProjectRequest.class), any(String.class)))
                .thenReturn(response(7L, "leader-user"));

        mockMvc.perform(patch("/projects/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed project", "status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        ArgumentCaptor<UpdateProjectRequest> request = ArgumentCaptor.forClass(UpdateProjectRequest.class);
        verify(projectService).update(
                org.mockito.ArgumentMatchers.eq(7L),
                request.capture(),
                org.mockito.ArgumentMatchers.eq(CURRENT_EMAIL)
        );
        assertThat(request.getValue().getName()).isEqualTo("Renamed project");
        assertThat(request.getValue().getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    void rejectsBlankNameOnPatch() throws Exception {
        mockMvc.perform(patch("/projects/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" "}
                                """))
                .andExpect(status().isBadRequest());

        verify(projectService, never()).update(any(), any(), any());
    }

    @Test
    void changesPrimaryLeaderAndReturnsTheCompleteLeaderSet() throws Exception {
        when(projectService.changeLeader(any(Long.class), any(ChangeProjectLeaderRequest.class), any(String.class)))
                .thenReturn(response(7L, "new-leader", "old-leader"));

        mockMvc.perform(patch("/projects/7/leader")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leaderUserId":"new-leader"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryLeader.userId").value("new-leader"))
                .andExpect(jsonPath("$.leaders[0].userId").value("new-leader"))
                .andExpect(jsonPath("$.leaders[1].userId").value("old-leader"));

        ArgumentCaptor<ChangeProjectLeaderRequest> request =
                ArgumentCaptor.forClass(ChangeProjectLeaderRequest.class);
        verify(projectService).changeLeader(
                org.mockito.ArgumentMatchers.eq(7L),
                request.capture(),
                org.mockito.ArgumentMatchers.eq(CURRENT_EMAIL)
        );
        assertThat(request.getValue().getLeaderUserId()).isEqualTo("new-leader");
    }

    @Test
    void rejectsBlankLeaderChange() throws Exception {
        mockMvc.perform(patch("/projects/7/leader")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leaderUserId":" "}
                                """))
                .andExpect(status().isBadRequest());

        verify(projectService, never()).changeLeader(any(), any(), any());
    }

    @Test
    void replacesTheCompleteLeaderSet() throws Exception {
        when(projectService.replaceLeaders(any(Long.class), any(ChangeProjectLeadersRequest.class), any(String.class)))
                .thenReturn(response(7L, "leader-user", "co-leader"));

        mockMvc.perform(put("/projects/7/leaders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leaderUserIds":["leader-user", "co-leader"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaders.length()").value(2))
                .andExpect(jsonPath("$.leaders[1].userId").value("co-leader"));

        ArgumentCaptor<ChangeProjectLeadersRequest> request =
                ArgumentCaptor.forClass(ChangeProjectLeadersRequest.class);
        verify(projectService).replaceLeaders(
                org.mockito.ArgumentMatchers.eq(7L),
                request.capture(),
                org.mockito.ArgumentMatchers.eq(CURRENT_EMAIL)
        );
        assertThat(request.getValue().getLeaderUserIds())
                .containsExactly("leader-user", "co-leader");
    }

    @Test
    void acceptsAnEmptyCompleteLeaderSet() throws Exception {
        when(projectService.replaceLeaders(any(Long.class), any(ChangeProjectLeadersRequest.class), any(String.class)))
                .thenReturn(response(7L, null));

        mockMvc.perform(put("/projects/7/leaders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leaderUserIds":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryLeader").doesNotExist())
                .andExpect(jsonPath("$.leaders").isEmpty());

        ArgumentCaptor<ChangeProjectLeadersRequest> request =
                ArgumentCaptor.forClass(ChangeProjectLeadersRequest.class);
        verify(projectService).replaceLeaders(
                org.mockito.ArgumentMatchers.eq(7L),
                request.capture(),
                org.mockito.ArgumentMatchers.eq(CURRENT_EMAIL)
        );
        assertThat(request.getValue().getLeaderUserIds()).isEmpty();
    }

    @Test
    void updatesCompleteLeadershipAtomically() throws Exception {
        when(projectService.updateLeadership(
                any(Long.class),
                any(UpdateProjectLeadershipRequest.class),
                any(String.class)
        )).thenReturn(response(7L, "leader-user", "co-leader"));

        mockMvc.perform(put("/projects/7/leadership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "primaryLeaderUserId":"leader-user",
                                  "leaderUserIds":["leader-user", "co-leader"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryLeader.userId").value("leader-user"))
                .andExpect(jsonPath("$.leaders.length()").value(2));

        ArgumentCaptor<UpdateProjectLeadershipRequest> request =
                ArgumentCaptor.forClass(UpdateProjectLeadershipRequest.class);
        verify(projectService).updateLeadership(
                org.mockito.ArgumentMatchers.eq(7L),
                request.capture(),
                org.mockito.ArgumentMatchers.eq(CURRENT_EMAIL)
        );
        assertThat(request.getValue().getPrimaryLeaderUserId()).isEqualTo("leader-user");
        assertThat(request.getValue().getLeaderUserIds())
                .containsExactly("leader-user", "co-leader");
    }

    @Test
    void rejectsPrimaryLeaderOutsideCompleteLeaderSet() throws Exception {
        mockMvc.perform(put("/projects/7/leadership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "primaryLeaderUserId":"outside-user",
                                  "leaderUserIds":["leader-user"]
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(projectService, never()).updateLeadership(any(), any(), any());
    }

    @Test
    void softDeletesWithNoContentStatus() throws Exception {
        mockMvc.perform(delete("/projects/7"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(projectService).delete(7L, CURRENT_EMAIL);
    }

    private static ProjectResponse response(Long id, String primaryLeaderUserId, String... additionalLeaderUserIds) {
        List<ProjectLeaderResponse> leaders = new ArrayList<>();
        ProjectLeaderResponse primaryLeader = null;
        if (primaryLeaderUserId != null) {
            primaryLeader = leader(primaryLeaderUserId);
            leaders.add(primaryLeader);
        }
        for (String userId : additionalLeaderUserIds) {
            leaders.add(leader(userId));
        }

        return ProjectResponse.builder()
                .id(id)
                .code("SL-AI")
                .name("Smart Lab AI")
                .projectType(ProjectType.RESEARCH)
                .status(ProjectStatus.PROPOSED)
                .isPublic(false)
                .isFeatured(false)
                .primaryLeader(primaryLeader)
                .leaders(leaders)
                .build();
    }

    private static ProjectLeaderResponse leader(String userId) {
        return ProjectLeaderResponse.builder()
                .userId(userId)
                .name(userId)
                .build();
    }
}
