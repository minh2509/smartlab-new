package com.smartlab.controller;

import com.smartlab.dto.response.PublicDocumentSummaryResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.PublicDocumentFileType;
import com.smartlab.enums.PublicDocumentSort;
import com.smartlab.service.PublicDocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicDocumentControllerTest {
    private final PublicDocumentService service = mock(PublicDocumentService.class);
    private final PublicDocumentController controller = new PublicDocumentController(service);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    void forwardsPublicArchiveFiltersAndDoesNotExposeInternalMetadata() throws Exception {
        when(service.list("robot", 7L, PublicDocumentFileType.PDF, 2026, PublicDocumentSort.TITLE_ASC, 2, 12))
                .thenReturn(new PublicPageResponse<>(List.of(document()), 2, 12, 1, 1));

        mockMvc.perform(get("/documents/public")
                        .param("q", "robot")
                        .param("projectId", "7")
                        .param("fileType", "PDF")
                        .param("year", "2026")
                        .param("sort", "TITLE_ASC")
                        .param("page", "2")
                        .param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Public guide"))
                .andExpect(jsonPath("$.items[0].createdBy").doesNotExist())
                .andExpect(jsonPath("$.items[0].storageKey").doesNotExist());

        verify(service).list("robot", 7L, PublicDocumentFileType.PDF, 2026, PublicDocumentSort.TITLE_ASC, 2, 12);
    }

    @Test
    void publicArchiveUsesSafeDefaultsAndYearsEndpointIsPublicContract() throws Exception {
        when(service.list(null, null, PublicDocumentFileType.ALL, null, PublicDocumentSort.LATEST, 0, 12))
                .thenReturn(new PublicPageResponse<>(List.of(), 0, 12, 0, 0));
        when(service.years()).thenReturn(List.of(2026, 2025));

        mockMvc.perform(get("/documents/public")).andExpect(status().isOk());
        mockMvc.perform(get("/documents/public/years")).andExpect(status().isOk()).andExpect(jsonPath("$[0]").value(2026));
        verify(service).list(null, null, PublicDocumentFileType.ALL, null, PublicDocumentSort.LATEST, 0, 12);
        verify(service).years();
    }

    private static PublicDocumentSummaryResponse document() {
        return new PublicDocumentSummaryResponse(31L, "Public guide", "Description", 7L, "AI", "AI Project", 101L,
                "guide.pdf", "application/pdf", 1024L, 3, Instant.parse("2026-08-22T00:00:00Z"));
    }
}
