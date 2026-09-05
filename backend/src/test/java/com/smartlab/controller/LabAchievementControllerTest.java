package com.smartlab.controller;

import com.smartlab.dto.request.CreateLabAchievementRequest;
import com.smartlab.dto.request.UpdateLabAchievementRequest;
import com.smartlab.dto.response.AdminLabAchievementResponse;
import com.smartlab.dto.response.LabAchievementResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.dto.response.PublicRelatedProjectResponse;
import com.smartlab.enums.AchievementType;
import com.smartlab.service.LabAchievementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LabAchievementControllerTest {
    @Mock private LabAchievementService achievementService;
    @InjectMocks private LabAchievementController controller;

    @Test void routesPublicListAndCreate() throws Exception {
        when(achievementService.listPublic(2026, 1, 8)).thenReturn(new PublicPageResponse<>(List.of(response()), 1, 8, 1, 1));
        when(achievementService.create(any())).thenReturn(adminResponse());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/achievements").queryParam("year", "2026").queryParam("page", "1").queryParam("size", "8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].achievementType").value("AWARD"))
                .andExpect(jsonPath("$.items[0].relatedProject.id").value(7))
                .andExpect(jsonPath("$.items[0].relatedProject.code").value("SL-7"))
                .andExpect(jsonPath("$.items[0].relatedProject.name").value("Project 7"))
                .andExpect(jsonPath("$.items[0].recognizingOrganization").value("Public organization"))
                .andExpect(jsonPath("$.items[0].relatedProjectId").doesNotExist());
        mvc.perform(post("/admin/achievements").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Award\",\"achievementType\":\"AWARD\",\"achievementYear\":2026}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.relatedProjectId").value(7))
                .andExpect(jsonPath("$.recognizingOrganization").value("Admin organization"));
        verify(achievementService).listPublic(2026, 1, 8);
        ArgumentCaptor<CreateLabAchievementRequest> create = ArgumentCaptor.forClass(CreateLabAchievementRequest.class);
        verify(achievementService).create(create.capture()); assertThat(create.getValue().getAchievementYear()).isEqualTo(2026);
    }

    @Test void forwardsAdminListFiltersAndReturnsStoredRelatedProjectId() throws Exception {
        when(achievementService.listAdmin(2026, AchievementType.AWARD, false, "robot", 1, 20))
                .thenReturn(new PublicPageResponse<>(List.of(adminResponse()), 1, 20, 1, 1));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/admin/achievements").queryParam("year", "2026").queryParam("type", "AWARD")
                        .queryParam("isPublic", "false").queryParam("q", "robot").queryParam("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].relatedProjectId").value(7))
                .andExpect(jsonPath("$.items[0].relatedProject").doesNotExist());
        verify(achievementService).listAdmin(2026, AchievementType.AWARD, false, "robot", 1, 20);
    }

    @Test void patchTracksExplicitNullWithoutClientSpoofablePresenceFlags() throws Exception {
        when(achievementService.update(eq(7L), any())).thenReturn(adminResponse());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(patch("/admin/achievements/7").contentType(MediaType.APPLICATION_JSON).content("""
                {"summary":null,"achievementDate":null,"evidenceUrl":null,"recognizingOrganization":null,"relatedProjectId":null,
                 "summaryPresent":false,"achievementDatePresent":false,"evidenceUrlPresent":false,"recognizingOrganizationPresent":false,"relatedProjectIdPresent":false}
                """)) .andExpect(status().isOk());
        ArgumentCaptor<UpdateLabAchievementRequest> update = ArgumentCaptor.forClass(UpdateLabAchievementRequest.class);
        verify(achievementService).update(eq(7L), update.capture());
        assertThat(update.getValue().isSummaryPresent()).isTrue(); assertThat(update.getValue().isAchievementDatePresent()).isTrue();
        assertThat(update.getValue().isEvidenceUrlPresent()).isTrue(); assertThat(update.getValue().isRecognizingOrganizationPresent()).isTrue(); assertThat(update.getValue().isRelatedProjectIdPresent()).isTrue();
    }

    @Test void rejectsRecognizingOrganizationOverFiveHundredCharacters() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/admin/achievements").contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"Award","achievementType":"AWARD","achievementYear":2026,"recognizingOrganization":"%s"}
                """.formatted("x".repeat(501))))
                .andExpect(status().isBadRequest());
    }

    private static LabAchievementResponse response() {
        return LabAchievementResponse.builder().id(7L).title("Award").achievementType(AchievementType.AWARD).achievementYear(2026)
                .recognizingOrganization("Public organization")
                .relatedProject(new PublicRelatedProjectResponse(7L, "SL-7", "Project 7"))
                .isPublic(true).createdAt(Instant.parse("2026-08-20T08:00:00Z")).updatedAt(Instant.parse("2026-08-20T08:00:00Z")).build();
    }

    private static AdminLabAchievementResponse adminResponse() {
        return new AdminLabAchievementResponse(7L, "Award", null, AchievementType.AWARD, 2026, null, null, "Admin organization", 7L,
                false, Instant.parse("2026-08-20T08:00:00Z"), Instant.parse("2026-08-20T08:00:00Z"));
    }
}
