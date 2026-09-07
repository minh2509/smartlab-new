package com.smartlab.controller;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
import com.smartlab.dto.request.CreateEventRequest;
import com.smartlab.dto.request.UpdateEventRequest;
import com.smartlab.dto.response.EventResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.EventMode;
import com.smartlab.enums.EventStatus;
import com.smartlab.enums.EventVisibility;
import com.smartlab.filter.JwtRequestFilter;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.EventService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, JwtRequestFilter.class})
class EventControllerSecurityTest {
    private static final String EMAIL = "member@smartlab.test";
    private static final String CREATE_BODY = """
            {
              "title":"Lab meetup",
              "mode":"IN_PERSON",
              "location":"Room A",
              "startAt":"2026-08-20T08:00:00Z",
              "visibility":"LAB"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;
    @MockitoBean
    private AppUserDetailService appUserDetailService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserSessionService userSessionService;

    @Test
    void anonymousCanReadPublicEventsButCannotReachManagementEndpoints() throws Exception {
        mockMvc.perform(get("/events/public")).andExpect(status().isOk());
        mockMvc.perform(get("/events")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/events/41")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/events/41")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/events/41")).andExpect(status().isUnauthorized());

        verify(eventService).listPublic(null, null, null, null);
        verifyNoMoreInteractions(eventService);
    }

    @Test
    void anonymousCanReadPublicEventArchiveAndDetail() throws Exception {
        when(eventService.listPublicArchive(0, 12, null, null, null))
                .thenReturn(new PublicPageResponse<>(List.of(response()), 0, 12, 1, 1));
        when(eventService.getPublic(41L)).thenReturn(response());

        mockMvc.perform(get("/events/public/archive"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].id").value(41));
        mockMvc.perform(get("/events/public/41"))
                .andExpect(status().isOk());

        verify(eventService).listPublicArchive(0, 12, null, null, null);
        verify(eventService).getPublic(41L);
    }

    @Test
    void authenticatedAccountReachesServiceForDynamicVisibilityAndLeadershipChecks() throws Exception {
        EventResponse response = response();
        when(eventService.list(EMAIL, null, null, null)).thenReturn(List.of(response));
        when(eventService.get(41L, EMAIL)).thenReturn(response);
        when(eventService.create(any(CreateEventRequest.class), eq(EMAIL))).thenReturn(response);
        when(eventService.update(eq(41L), any(UpdateEventRequest.class), eq(EMAIL))).thenReturn(response);

        mockMvc.perform(get("/events").with(user(EMAIL))).andExpect(status().isOk());
        mockMvc.perform(get("/events/41").with(user(EMAIL))).andExpect(status().isOk());
        mockMvc.perform(post("/events")
                        .with(user(EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/events/41")
                        .with(user(EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/events/41").with(user(EMAIL))).andExpect(status().isNoContent());

        verify(eventService).list(EMAIL, null, null, null);
        verify(eventService).get(41L, EMAIL);
        verify(eventService).create(any(CreateEventRequest.class), eq(EMAIL));
        verify(eventService).update(eq(41L), any(UpdateEventRequest.class), eq(EMAIL));
        verify(eventService).delete(41L, EMAIL);
    }

    private static EventResponse response() {
        return EventResponse.builder()
                .id(41L)
                .title("Lab meetup")
                .mode(EventMode.IN_PERSON)
                .location("Room A")
                .startAt(Instant.parse("2026-08-20T08:00:00Z"))
                .status(EventStatus.SCHEDULED)
                .visibility(EventVisibility.LAB)
                .createdAt(Instant.parse("2026-08-12T08:00:00Z"))
                .updatedAt(Instant.parse("2026-08-12T08:00:00Z"))
                .build();
    }
}
