package com.smartlab.controller;

import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.service.GalleryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GalleryControllerTest {
    private final GalleryService service = mock(GalleryService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new GalleryController(service)).build();

    @Test
    void forwardsPublicGalleryDefaultsAndFilters() throws Exception {
        when(service.listPublic(null, null, null, null, com.smartlab.enums.PublicGallerySort.LATEST, 0, 24))
                .thenReturn(new PublicPageResponse<>(List.of(), 0, 24, 0, 0));
        mockMvc.perform(get("/gallery/public")).andExpect(status().isOk());
        verify(service).listPublic(null, null, null, null, com.smartlab.enums.PublicGallerySort.LATEST, 0, 24);
    }

    @Test
    void yearsEndpointReturnsPublicYears() throws Exception {
        when(service.listPublicYears()).thenReturn(List.of(2026));
        mockMvc.perform(get("/gallery/public/years")).andExpect(status().isOk());
        verify(service).listPublicYears();
    }
}
