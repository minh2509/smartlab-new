package com.smartlab.controller;

import com.smartlab.dto.request.CreateResearchPublicationRequest;
import com.smartlab.dto.request.UpdateResearchPublicationRequest;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.dto.response.ResearchPublicationResponse;
import com.smartlab.enums.PublicationType;
import com.smartlab.service.ResearchPublicationService;
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
class ResearchPublicationControllerTest {
    private static final String CREATE_BODY = """
            {"title":"Publication","authors":"A. Author","publicationType":"JOURNAL_ARTICLE","venue":"Venue","publicationYear":2026}
            """;

    @Mock private ResearchPublicationService publicationService;
    @InjectMocks private ResearchPublicationController controller;

    @Test
    void forwardsPublicListYearAndPagination() throws Exception {
        when(publicationService.listPublic(2026, 1, 8))
                .thenReturn(new PublicPageResponse<>(List.of(response()), 1, 8, 9, 2));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/publications").queryParam("year", "2026").queryParam("page", "1").queryParam("size", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(7));

        verify(publicationService).listPublic(2026, 1, 8);
    }

    @Test
    void mapsValidCreateAndPatchRequests() throws Exception {
        when(publicationService.create(any(CreateResearchPublicationRequest.class))).thenReturn(response());
        when(publicationService.update(eq(7L), any(UpdateResearchPublicationRequest.class))).thenReturn(response());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/admin/publications").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
        mockMvc.perform(patch("/admin/publications/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated publication\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        ArgumentCaptor<CreateResearchPublicationRequest> create = ArgumentCaptor.forClass(CreateResearchPublicationRequest.class);
        ArgumentCaptor<UpdateResearchPublicationRequest> update = ArgumentCaptor.forClass(UpdateResearchPublicationRequest.class);
        verify(publicationService).create(create.capture());
        verify(publicationService).update(eq(7L), update.capture());
        assertThat(create.getValue().getPublicationYear()).isEqualTo(2026);
        assertThat(update.getValue().getTitle()).isEqualTo("Updated publication");
    }

    @Test
    void patchIgnoresClientSuppliedOptionalFieldPresenceFlags() throws Exception {
        when(publicationService.update(eq(7L), any(UpdateResearchPublicationRequest.class))).thenReturn(response());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(patch("/admin/publications/7").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"publicationDatePresent":true,"doiPresent":true,"publicUrlPresent":true,"summaryPresent":true}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateResearchPublicationRequest> update = ArgumentCaptor.forClass(UpdateResearchPublicationRequest.class);
        verify(publicationService).update(eq(7L), update.capture());
        assertThat(update.getValue().isPublicationDatePresent()).isFalse();
        assertThat(update.getValue().isDoiPresent()).isFalse();
        assertThat(update.getValue().isPublicUrlPresent()).isFalse();
        assertThat(update.getValue().isSummaryPresent()).isFalse();
    }

    @Test
    void patchTracksExplicitNullOptionalFields() throws Exception {
        when(publicationService.update(eq(7L), any(UpdateResearchPublicationRequest.class))).thenReturn(response());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(patch("/admin/publications/7").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"publicationDate":null,"doi":null,"publicUrl":null,"summary":null}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateResearchPublicationRequest> update = ArgumentCaptor.forClass(UpdateResearchPublicationRequest.class);
        verify(publicationService).update(eq(7L), update.capture());
        assertThat(update.getValue().isPublicationDatePresent()).isTrue();
        assertThat(update.getValue().isDoiPresent()).isTrue();
        assertThat(update.getValue().isPublicUrlPresent()).isTrue();
        assertThat(update.getValue().isSummaryPresent()).isTrue();
        assertThat(update.getValue().getPublicationDate()).isNull();
        assertThat(update.getValue().getDoi()).isNull();
        assertThat(update.getValue().getPublicUrl()).isNull();
        assertThat(update.getValue().getSummary()).isNull();
    }

    private static ResearchPublicationResponse response() {
        return ResearchPublicationResponse.builder()
                .id(7L).title("Publication").authors("A. Author")
                .publicationType(PublicationType.JOURNAL_ARTICLE).venue("Venue").publicationYear(2026)
                .isPublic(true).createdAt(Instant.parse("2026-08-20T08:00:00Z"))
                .updatedAt(Instant.parse("2026-08-20T08:00:00Z")).build();
    }
}
