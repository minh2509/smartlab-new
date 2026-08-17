package com.smartlab.controller;

import com.smartlab.dto.request.CreateEventRequest;
import com.smartlab.dto.request.UpdateEventRequest;
import com.smartlab.dto.response.EventCreatorResponse;
import com.smartlab.dto.response.EventResponse;
import com.smartlab.enums.EventMode;
import com.smartlab.enums.EventStatus;
import com.smartlab.enums.EventVisibility;
import com.smartlab.service.EventService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.CurrentSecurityContextArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {
    private static final String EMAIL = "member@smartlab.test";

    @Mock
    private EventService eventService;

    @InjectMocks
    private EventController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL, null)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new CurrentSecurityContextArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsWithEverySupportedFilterAndSerializesContract() throws Exception {
        when(eventService.list(EMAIL, 7L, EventStatus.SCHEDULED, true))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/events")
                        .queryParam("projectId", "7")
                        .queryParam("status", "SCHEDULED")
                        .queryParam("upcoming", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(41))
                .andExpect(jsonPath("$[0].projectId").value(7))
                .andExpect(jsonPath("$[0].content").value("Demo content"))
                .andExpect(jsonPath("$[0].mode").value("ONLINE"))
                .andExpect(jsonPath("$[0].meetingUrl").value("https://meet.example/demo"))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].visibility").value("PROJECT"))
                .andExpect(jsonPath("$[0].creator.userId").value("creator-user"));

        verify(eventService).list(EMAIL, 7L, EventStatus.SCHEDULED, true);
    }

    @Test
    void listsPublicEventsWithoutViewerContext() throws Exception {
        when(eventService.listPublic(EventStatus.SCHEDULED, true))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/events/public")
                        .queryParam("status", "SCHEDULED")
                        .queryParam("upcoming", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(41));

        verify(eventService).listPublic(EventStatus.SCHEDULED, true);
    }

    @Test
    void getsVisibleDetailAndPreservesHiddenNotFoundStatus() throws Exception {
        when(eventService.get(41L, EMAIL)).thenReturn(response());
        when(eventService.get(42L, EMAIL))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        mockMvc.perform(get("/events/41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(41));
        mockMvc.perform(get("/events/42"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsEventWithNullableContentAndEndTime() throws Exception {
        when(eventService.create(any(CreateEventRequest.class), eq(EMAIL))).thenReturn(response());

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Online demo",
                                  "mode":"ONLINE",
                                  "meetingUrl":"https://meet.example/demo",
                                  "startAt":"2026-08-20T08:00:00Z",
                                  "visibility":"LAB"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(41));

        ArgumentCaptor<CreateEventRequest> request = ArgumentCaptor.forClass(CreateEventRequest.class);
        verify(eventService).create(request.capture(), eq(EMAIL));
        assertThat(request.getValue().getContent()).isNull();
        assertThat(request.getValue().getEndAt()).isNull();
        assertThat(request.getValue().getStatus()).isEqualTo(EventStatus.SCHEDULED);
    }

    @Test
    void rejectsInvalidModeFieldsUrlAndTimeBeforeService() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Invalid online event",
                                  "mode":"ONLINE",
                                  "location":"Room A",
                                  "meetingUrl":"ftp://meet.example/demo",
                                  "startAt":"2026-08-20T10:00:00Z",
                                  "endAt":"2026-08-20T08:00:00Z",
                                  "visibility":"LAB"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(eventService, never()).create(any(), any());
    }

    @Test
    void rejectsInconsistentVisibilityAndProjectBeforeService() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Lab event",
                                  "mode":"IN_PERSON",
                                  "location":"Room A",
                                  "startAt":"2026-08-20T08:00:00Z",
                                  "visibility":"PROJECT"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(eventService, never()).create(any(), any());
    }

    @Test
    void patchesMutableFields() throws Exception {
        when(eventService.update(eq(41L), any(UpdateEventRequest.class), eq(EMAIL)))
                .thenReturn(response());

        mockMvc.perform(patch("/events/41")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Updated demo",
                                  "status":"COMPLETED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(41));

        ArgumentCaptor<UpdateEventRequest> request = ArgumentCaptor.forClass(UpdateEventRequest.class);
        verify(eventService).update(eq(41L), request.capture(), eq(EMAIL));
        assertThat(request.getValue().getTitle()).isEqualTo("Updated demo");
        assertThat(request.getValue().getStatus()).isEqualTo(EventStatus.COMPLETED);
        assertThat(request.getValue().isProjectIdPresent()).isFalse();
    }

    @Test
    void patchDeserializesExplicitNullForClearableFields() throws Exception {
        when(eventService.update(eq(41L), any(UpdateEventRequest.class), eq(EMAIL)))
                .thenReturn(response());

        mockMvc.perform(patch("/events/41")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":null,\"endAt\":null}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateEventRequest> request = ArgumentCaptor.forClass(UpdateEventRequest.class);
        verify(eventService).update(eq(41L), request.capture(), eq(EMAIL));
        assertThat(request.getValue().isContentPresent()).isTrue();
        assertThat(request.getValue().getContent()).isNull();
        assertThat(request.getValue().isEndAtPresent()).isTrue();
        assertThat(request.getValue().getEndAt()).isNull();
    }

    @Test
    void rejectsProjectAssociationOnPatchEvenWhenExplicitlyNull() throws Exception {
        mockMvc.perform(patch("/events/41")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":null}"))
                .andExpect(status().isBadRequest());

        verify(eventService, never()).update(any(), any(), any());
    }

    @Test
    void rejectsUnknownEnumAndSoftDeletesWithNoContent() throws Exception {
        mockMvc.perform(get("/events").queryParam("status", "OPEN"))
                .andExpect(status().isBadRequest());
        verify(eventService, never()).list(any(), any(), any(), any());

        mockMvc.perform(delete("/events/41"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(eventService).delete(41L, EMAIL);
    }

    @Test
    void requiresAuthenticationAtControllerBoundary() {
        PreAuthorize annotation = EventController.class.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("isAuthenticated()");
    }

    private static EventResponse response() {
        return EventResponse.builder()
                .id(41L)
                .projectId(7L)
                .title("Online demo")
                .content("Demo content")
                .mode(EventMode.ONLINE)
                .meetingUrl("https://meet.example/demo")
                .startAt(Instant.parse("2026-08-20T08:00:00Z"))
                .endAt(Instant.parse("2026-08-20T10:00:00Z"))
                .status(EventStatus.SCHEDULED)
                .visibility(EventVisibility.PROJECT)
                .creator(EventCreatorResponse.builder().userId("creator-user").name("Creator").build())
                .createdAt(Instant.parse("2026-08-12T08:00:00Z"))
                .updatedAt(Instant.parse("2026-08-12T08:00:00Z"))
                .build();
    }
}
