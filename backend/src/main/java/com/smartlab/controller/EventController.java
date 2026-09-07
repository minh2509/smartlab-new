package com.smartlab.controller;

import com.smartlab.dto.request.CreateEventRequest;
import com.smartlab.dto.request.UpdateEventRequest;
import com.smartlab.dto.response.EventResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.EventStatus;
import com.smartlab.enums.PublicEventSort;
import com.smartlab.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@RequestMapping("/events")
@Tag(name = "Events", description = "Lab and project events without participant registration.")
public class EventController {
    private final EventService eventService;

    @GetMapping("/public")
    @PreAuthorize("permitAll()")
    @Operation(summary = "List public events")
    public List<EventResponse> listPublic(
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) Boolean upcoming,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) PublicEventSort sort
    ) {
        return eventService.listPublic(status, upcoming, limit, sort);
    }

    @GetMapping("/public/archive")
    @PreAuthorize("permitAll()")
    @Operation(summary = "List public events with server-side pagination and filters")
    public PublicPageResponse<EventResponse> listPublicArchive(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) Boolean upcoming,
            @RequestParam(name = "q", required = false) String query
    ) {
        return eventService.listPublicArchive(page, size, status, upcoming, query);
    }

    @GetMapping("/public/{id}")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Get one public event")
    public EventResponse getPublic(@PathVariable @Positive(message = "Event id must be positive") Long id) {
        return eventService.getPublic(id);
    }

    @GetMapping
    @Operation(summary = "List events visible to the authenticated account")
    public List<EventResponse> list(
            @RequestParam(required = false)
            @Positive(message = "Project id must be positive") Long projectId,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) Boolean upcoming,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return eventService.list(currentEmail, projectId, status, upcoming);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one visible event")
    public EventResponse get(
            @PathVariable @Positive(message = "Event id must be positive") Long id,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return eventService.get(id, currentEmail);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a lab or project event")
    public EventResponse create(
            @Valid @RequestBody CreateEventRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return eventService.create(request, currentEmail);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update an event", description = "The project association is immutable.")
    public EventResponse update(
            @PathVariable @Positive(message = "Event id must be positive") Long id,
            @Valid @RequestBody UpdateEventRequest request,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        return eventService.update(id, request, currentEmail);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete an event")
    public void delete(
            @PathVariable @Positive(message = "Event id must be positive") Long id,
            @CurrentSecurityContext(expression = "authentication?.name") String currentEmail
    ) {
        eventService.delete(id, currentEmail);
    }
}
