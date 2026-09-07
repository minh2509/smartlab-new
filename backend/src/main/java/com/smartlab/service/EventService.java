package com.smartlab.service;

import com.smartlab.dto.request.CreateEventRequest;
import com.smartlab.dto.request.UpdateEventRequest;
import com.smartlab.dto.response.EventResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.EventStatus;
import com.smartlab.enums.PublicEventSort;

import java.util.List;

public interface EventService {
    List<EventResponse> listPublic(
            EventStatus status,
            Boolean upcoming
    );

    List<EventResponse> listPublic(EventStatus status, Boolean upcoming, Integer limit, PublicEventSort sort);

    PublicPageResponse<EventResponse> listPublicArchive(
            int page,
            int size,
            EventStatus status,
            Boolean upcoming,
            String query
    );

    EventResponse getPublic(Long eventId);

    List<EventResponse> list(
            String authenticatedEmail,
            Long projectId,
            EventStatus status,
            Boolean upcoming
    );

    EventResponse get(Long eventId, String authenticatedEmail);

    EventResponse create(CreateEventRequest request, String authenticatedEmail);

    EventResponse update(Long eventId, UpdateEventRequest request, String authenticatedEmail);

    void delete(Long eventId, String authenticatedEmail);
}
