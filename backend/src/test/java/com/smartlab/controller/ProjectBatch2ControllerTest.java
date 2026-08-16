package com.smartlab.controller;

import com.smartlab.dto.request.AddProjectMemberRequest;
import com.smartlab.dto.request.ReplaceProjectResearchFieldsRequest;
import com.smartlab.dto.response.ProjectMemberCandidateResponse;
import com.smartlab.dto.response.ProjectMemberResponse;
import com.smartlab.dto.response.ProjectResearchFieldResponse;
import com.smartlab.dto.response.ProjectResponse;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import com.smartlab.service.ProjectMemberService;
import com.smartlab.service.ProjectResearchFieldService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProjectBatch2ControllerTest {
    private static final String EMAIL = "leader@smartlab.test";

    @Mock private ProjectMemberService memberService;
    @Mock private ProjectResearchFieldService researchFieldService;
    @Mock private ProjectService projectService;
    @InjectMocks private ProjectMemberController memberController;
    @InjectMocks private ProjectResearchFieldController researchFieldController;
    @InjectMocks private ProjectController projectController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL, null)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(
                        projectController,
                        memberController,
                        researchFieldController
                )
                .setCustomArgumentResolvers(new CurrentSecurityContextArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsRequestedMembershipHistoryStatus() throws Exception {
        when(memberService.list(7L, ProjectMemberStatus.REMOVED, EMAIL)).thenReturn(List.of(
                ProjectMemberResponse.builder()
                        .userId("member-user").name("Member").email("member@test")
                        .projectRole(ProjectRole.MEMBER).status(ProjectMemberStatus.REMOVED).build()
        ));

        mockMvc.perform(get("/projects/7/members").queryParam("status", "REMOVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("member-user"))
                .andExpect(jsonPath("$[0].status").value("REMOVED"));
    }

    @Test
    void createsMemberAndDelegatesTrimCapableRequest() throws Exception {
        when(memberService.add(eq(7L), any(AddProjectMemberRequest.class), eq(EMAIL))).thenReturn(
                ProjectMemberResponse.builder().userId("member-user")
                        .projectRole(ProjectRole.MEMBER).status(ProjectMemberStatus.ACTIVE).build()
        );

        mockMvc.perform(post("/projects/7/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"member-user\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(memberService).add(eq(7L), any(AddProjectMemberRequest.class), eq(EMAIL));
    }

    @Test
    void rejectsBlankMemberUserIdBeforeService() throws Exception {
        mockMvc.perform(post("/projects/7/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"  \"}"))
                .andExpect(status().isBadRequest());

        verify(memberService, never()).add(any(), any(), any());
    }

    @Test
    void removesMemberByPublicUserId() throws Exception {
        mockMvc.perform(delete("/projects/7/members/member-user"))
                .andExpect(status().isNoContent());

        verify(memberService).remove(7L, "member-user", EMAIL);
    }

    @Test
    void returnsMemberCandidates() throws Exception {
        when(memberService.findCandidates(7L, "nguyen", EMAIL)).thenReturn(List.of(
                ProjectMemberCandidateResponse.builder()
                        .userId("candidate").name("Nguyen").email("nguyen@test").build()
        ));

        mockMvc.perform(get("/projects/7/member-candidates").queryParam("query", "nguyen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("nguyen@test"));
    }

    @Test
    void replacesResearchFieldsIncludingEmptyClearContract() throws Exception {
        when(researchFieldService.replace(eq(7L), any(), eq(EMAIL))).thenReturn(List.of());

        mockMvc.perform(patch("/projects/7/research-fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        ArgumentCaptor<ReplaceProjectResearchFieldsRequest> request =
                ArgumentCaptor.forClass(ReplaceProjectResearchFieldsRequest.class);
        verify(researchFieldService).replace(eq(7L), request.capture(), eq(EMAIL));
        assertThat(request.getValue().getFieldIds()).isEmpty();
    }

    @Test
    void listsResearchFieldsWithActiveState() throws Exception {
        when(researchFieldService.list(7L, EMAIL)).thenReturn(List.of(
                ProjectResearchFieldResponse.builder()
                        .id(3L).code("AI").name("Artificial Intelligence").isActive(false).build()
        ));

        mockMvc.perform(get("/projects/7/research-fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("AI"))
                .andExpect(jsonPath("$[0].isActive").value(false));
    }

    @Test
    void filtersVisibleProjectsByResearchField() throws Exception {
        when(researchFieldService.filterVisibleProjects(3L, EMAIL)).thenReturn(List.of(
                ProjectResponse.builder().id(7L).code("SL-AI").build()
        ));

        mockMvc.perform(get("/projects").queryParam("researchFieldId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7));
    }

    @Test
    void keepsExistingUnfilteredProjectListMappingUnchanged() throws Exception {
        when(projectService.list(EMAIL)).thenReturn(List.of(
                ProjectResponse.builder().id(8L).code("SL-ALL").build()
        ));

        mockMvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(8));

        verify(projectService).list(EMAIL);
        verify(researchFieldService, never()).filterVisibleProjects(any(), any());
    }
}
