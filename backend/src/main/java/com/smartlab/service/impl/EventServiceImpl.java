package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateEventRequest;
import com.smartlab.dto.request.UpdateEventRequest;
import com.smartlab.dto.response.EventCreatorResponse;
import com.smartlab.dto.response.EventResponse;
import com.smartlab.entity.EventEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.EventMode;
import com.smartlab.enums.EventStatus;
import com.smartlab.enums.EventVisibility;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
import com.smartlab.repo.EventRepository;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.AuditService;
import com.smartlab.service.EventService;
import com.smartlab.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {
    private static final String ADMIN = "ADMIN";
    private static final String PROJECT_MANAGE = "PROJECT_MANAGE";
    private static final String PROJECT_READ = "PROJECT_READ";
    private static final String EVENT = "EVENT";
    private static final String EVENT_CREATED = "EVENT_CREATED";
    private static final String EVENT_UPDATED = "EVENT_UPDATED";
    private static final String EVENT_DELETED = "EVENT_DELETED";
    private static final int TITLE_MAX_LENGTH = 255;
    private static final int CONTENT_MAX_LENGTH = 20_000;
    private static final int LOCATION_MAX_LENGTH = 255;
    private static final int MEETING_URL_MAX_LENGTH = 2048;

    private final EventRepository eventRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<EventResponse> list(
            String authenticatedEmail,
            Long projectId,
            EventStatus status,
            Boolean upcoming
    ) {
        UserEntity viewer = requireCurrentUser(authenticatedEmail);
        validateOptionalProjectId(projectId);
        List<EventEntity> candidates = eventRepository.findActiveEvents(
                projectId,
                status,
                upcoming,
                Instant.now()
        );

        boolean admin = isAdmin(viewer);
        boolean canReadProjectEvents = admin || hasProjectRead(viewer);
        Set<Long> activeProjectIds = admin || !canReadProjectEvents
                ? Set.of()
                : Set.copyOf(projectMemberRepository.findActiveProjectIdsByUserId(viewer.getId()));
        List<EventEntity> readableEvents = candidates.stream()
                .filter(event -> admin || isReadableByRegularAccount(event, activeProjectIds))
                .toList();
        Map<Long, EventCreatorResponse> creators = findCreators(readableEvents);

        return readableEvents.stream()
                .map(event -> toResponse(event, creators.get(event.getCreatedByUserId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse get(Long eventId, String authenticatedEmail) {
        UserEntity viewer = requireCurrentUser(authenticatedEmail);
        EventEntity event = findActiveEvent(eventId);
        boolean admin = isAdmin(viewer);
        Set<Long> activeProjectIds = admin || !hasProjectRead(viewer)
                ? Set.of()
                : Set.copyOf(projectMemberRepository.findActiveProjectIdsByUserId(viewer.getId()));
        if (!admin && !isReadableByRegularAccount(event, activeProjectIds)) {
            throw eventNotFound();
        }
        return toResponse(event, findCreator(event.getCreatedByUserId()));
    }

    @Override
    @Transactional
    public EventResponse create(CreateEventRequest request, String authenticatedEmail) {
        if (request == null) {
            throw badRequest("Event request is required");
        }
        UserEntity creator = requireCurrentUser(authenticatedEmail);
        Long projectId = request.getProjectId();
        String title = normalizeRequiredText(request.getTitle());
        String content = normalizeOptionalText(request.getContent());
        String location = normalizeOptionalText(request.getLocation());
        String meetingUrl = normalizeOptionalText(request.getMeetingUrl());
        EventStatus status = request.getStatus() == null ? EventStatus.SCHEDULED : request.getStatus();

        validateEventState(
                projectId,
                title,
                content,
                request.getMode(),
                location,
                meetingUrl,
                request.getStartAt(),
                request.getEndAt(),
                status,
                request.getVisibility()
        );
        requireActiveProjectWhenAssociated(projectId);
        requireCanManage(projectId, request.getVisibility(), creator);

        Instant now = Instant.now();
        EventEntity event = EventEntity.create(
                projectId,
                title,
                content,
                request.getMode(),
                location,
                meetingUrl,
                request.getStartAt(),
                request.getEndAt(),
                status,
                request.getVisibility(),
                creator.getId(),
                now
        );
        EventEntity saved = eventRepository.saveAndFlush(event);
        auditService.log(EVENT_CREATED, EVENT, saved.getId().toString(), null, snapshot(saved));
        return toResponse(saved, toCreatorResponse(creator));
    }

    @Override
    @Transactional
    public EventResponse update(Long eventId, UpdateEventRequest request, String authenticatedEmail) {
        if (request == null) {
            throw badRequest("Event request is required");
        }
        if (request.isProjectIdPresent()) {
            throw badRequest("Project association is immutable");
        }
        if (request.isEmptyPatch()) {
            throw badRequest("At least one mutable event field is required");
        }
        UserEntity actor = requireCurrentUser(authenticatedEmail);
        EventEntity event = findActiveEventForUpdate(eventId);
        requireCanManage(event.getProjectId(), event.getVisibility(), actor);

        Map<String, Object> before = snapshot(event);
        EventMode mode = request.getMode() == null ? event.getMode() : request.getMode();
        String title = request.getTitle() == null
                ? event.getTitle()
                : normalizeRequiredText(request.getTitle());
        String content = !request.isContentPresent()
                ? event.getContent()
                : normalizeOptionalText(request.getContent());
        String location = request.getLocation() == null
                ? event.getLocation()
                : normalizeOptionalText(request.getLocation());
        String meetingUrl = request.getMeetingUrl() == null
                ? event.getMeetingUrl()
                : normalizeOptionalText(request.getMeetingUrl());

        if (request.getMode() != null && request.getMode() != event.getMode()) {
            if (mode == EventMode.IN_PERSON && request.getMeetingUrl() == null) {
                meetingUrl = null;
            }
            if (mode == EventMode.ONLINE && request.getLocation() == null) {
                location = null;
            }
        }

        Instant startAt = request.getStartAt() == null ? event.getStartAt() : request.getStartAt();
        Instant endAt = request.isEndAtPresent() ? request.getEndAt() : event.getEndAt();
        EventStatus status = request.getStatus() == null ? event.getStatus() : request.getStatus();
        EventVisibility visibility = request.getVisibility() == null
                ? event.getVisibility()
                : request.getVisibility();

        if (visibility != event.getVisibility()) {
            requireCanManage(event.getProjectId(), visibility, actor);
        }

        validateEventState(
                event.getProjectId(),
                title,
                content,
                mode,
                location,
                meetingUrl,
                startAt,
                endAt,
                status,
                visibility
        );
        requireActiveProjectWhenAssociated(event.getProjectId());

        if (hasSameMutableState(
                event, title, content, mode, location, meetingUrl,
                startAt, endAt, status, visibility
        )) {
            return toResponse(event, findCreator(event.getCreatedByUserId()));
        }

        event.applyUpdate(
                title,
                content,
                mode,
                location,
                meetingUrl,
                startAt,
                endAt,
                status,
                visibility,
                Instant.now()
        );
        EventEntity saved = eventRepository.saveAndFlush(event);
        auditService.log(EVENT_UPDATED, EVENT, saved.getId().toString(), before, snapshot(saved));
        return toResponse(saved, findCreator(saved.getCreatedByUserId()));
    }

    @Override
    @Transactional
    public void delete(Long eventId, String authenticatedEmail) {
        UserEntity actor = requireCurrentUser(authenticatedEmail);
        EventEntity event = findActiveEventForUpdate(eventId);
        requireCanManage(event.getProjectId(), event.getVisibility(), actor);
        Map<String, Object> before = snapshot(event);
        event.softDelete(Instant.now());
        EventEntity saved = eventRepository.saveAndFlush(event);
        auditService.log(EVENT_DELETED, EVENT, saved.getId().toString(), before, snapshot(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventResponse> listPublicEvents() {
        // Lấy tất cả event PUBLIC không phân biệt thời gian, loại bỏ CANCELLED
        List<EventEntity> publicEvents = eventRepository.findActiveEvents(
                null,
                null,
                null,
                Instant.now()
        ).stream()
                .filter(event -> event.getVisibility() == EventVisibility.PUBLIC)
                .filter(event -> event.getStatus() != EventStatus.CANCELLED)
                .toList();
        Map<Long, EventCreatorResponse> creators = findCreators(publicEvents);
        return publicEvents.stream()
                .map(event -> toResponse(event, creators.get(event.getCreatedByUserId())))
                .toList();
    }


    private void requireCanManage(Long projectId, EventVisibility visibility, UserEntity actor) {
        if (isAdmin(actor)
                && permissionService.getEffectivePermissionCodes(actor).contains(PROJECT_MANAGE)) {
            return;
        }
        if (visibility == EventVisibility.PROJECT
                && projectId != null
                && projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                projectId,
                actor.getId(),
                ProjectRole.LEADER,
                ProjectMemberStatus.ACTIVE
        )) {
            return;
        }
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                visibility == EventVisibility.PROJECT
                        ? "Only an administrator or an active leader of this project can manage the event"
                        : "Administrator role and project management permission are required"
        );
    }

    private boolean isReadableByRegularAccount(EventEntity event, Set<Long> activeProjectIds) {
        return event.getVisibility() == EventVisibility.PUBLIC
                || event.getVisibility() == EventVisibility.LAB
                || (event.getVisibility() == EventVisibility.PROJECT
                && event.getProjectId() != null
                && activeProjectIds.contains(event.getProjectId()));
    }

    private boolean isAdmin(UserEntity user) {
        return permissionService.getRoleCodes(user).contains(ADMIN);
    }

    private boolean hasProjectRead(UserEntity user) {
        return permissionService.getEffectivePermissionCodes(user).contains(PROJECT_READ);
    }

    private UserEntity requireCurrentUser(String email) {
        if (email == null || email.isBlank() || "anonymousUser".equals(email)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));
        if (!Boolean.TRUE.equals(user.getIsActive()) || permissionService.hasInactiveAssignedRole(user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated account is inactive");
        }
        return user;
    }

    private EventEntity findActiveEvent(Long eventId) {
        validateEventId(eventId);
        return eventRepository.findActiveById(eventId).orElseThrow(this::eventNotFound);
    }

    private EventEntity findActiveEventForUpdate(Long eventId) {
        validateEventId(eventId);
        return eventRepository.findActiveByIdForUpdate(eventId).orElseThrow(this::eventNotFound);
    }

    private void requireActiveProjectWhenAssociated(Long projectId) {
        if (projectId != null && projectRepository.findByIdAndDeletedAtIsNull(projectId).isEmpty()) {
            throw badRequest("Project is unavailable");
        }
    }

    private void validateEventState(
            Long projectId,
            String title,
            String content,
            EventMode mode,
            String location,
            String meetingUrl,
            Instant startAt,
            Instant endAt,
            EventStatus status,
            EventVisibility visibility
    ) {
        validateOptionalProjectId(projectId);
        validateRequiredLength(title, TITLE_MAX_LENGTH, "Event title");
        validateOptionalLength(content, CONTENT_MAX_LENGTH, "Event content");
        if (mode == null) {
            throw badRequest("Event mode is required");
        }
        if (status == null) {
            throw badRequest("Event status is required");
        }
        if (visibility == null) {
            throw badRequest("Event visibility is required");
        }
        if (startAt == null) {
            throw badRequest("Event start time is required");
        }
        if (endAt != null && !endAt.isAfter(startAt)) {
            throw badRequest("Event end time must be after start time");
        }
        if (location != null && location.length() > LOCATION_MAX_LENGTH) {
            throw badRequest("Event location must not exceed 255 characters");
        }
        if (meetingUrl != null && meetingUrl.length() > MEETING_URL_MAX_LENGTH) {
            throw badRequest("Meeting URL must not exceed 2048 characters");
        }
        if (mode == EventMode.IN_PERSON) {
            if (location == null || meetingUrl != null) {
                throw badRequest("In-person events require a location and must not include a meeting URL");
            }
        } else if (meetingUrl == null || location != null) {
            throw badRequest("Online events require a meeting URL and must not include a location");
        }
        if (meetingUrl != null) {
            validateMeetingUrl(meetingUrl);
        }
        if (visibility == EventVisibility.PROJECT && projectId == null) {
            throw badRequest("PROJECT visibility requires projectId");
        }
    }

    private void validateMeetingUrl(String meetingUrl) {
        try {
            URI uri = new URI(meetingUrl);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null) {
                throw badRequest("Meeting URL must be an absolute http(s) URL");
            }
        } catch (URISyntaxException exception) {
            throw badRequest("Meeting URL must be an absolute http(s) URL");
        }
    }

    private boolean hasSameMutableState(
            EventEntity event,
            String title,
            String content,
            EventMode mode,
            String location,
            String meetingUrl,
            Instant startAt,
            Instant endAt,
            EventStatus status,
            EventVisibility visibility
    ) {
        return Objects.equals(event.getTitle(), title)
                && Objects.equals(event.getContent(), content)
                && event.getMode() == mode
                && Objects.equals(event.getLocation(), location)
                && Objects.equals(event.getMeetingUrl(), meetingUrl)
                && Objects.equals(event.getStartAt(), startAt)
                && Objects.equals(event.getEndAt(), endAt)
                && event.getStatus() == status
                && event.getVisibility() == visibility;
    }

    private void validateEventId(Long eventId) {
        if (eventId == null || eventId <= 0) {
            throw badRequest("Event id must be positive");
        }
    }

    private void validateOptionalProjectId(Long projectId) {
        if (projectId != null && projectId <= 0) {
            throw badRequest("Project id must be positive");
        }
    }

    private void validateRequiredLength(String value, int maximum, String label) {
        if (value == null || value.isBlank()) {
            throw badRequest(label + " is required");
        }
        if (value.length() > maximum) {
            throw badRequest(label + " must not exceed " + maximum + " characters");
        }
    }

    private void validateOptionalLength(String value, int maximum, String label) {
        if (value != null && value.length() > maximum) {
            throw badRequest(label + " must not exceed " + maximum + " characters");
        }
    }

    private String normalizeRequiredText(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Map<Long, EventCreatorResponse> findCreators(List<EventEntity> events) {
        Set<Long> creatorIds = events.stream()
                .map(EventEntity::getCreatedByUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (creatorIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, EventCreatorResponse> creators = new HashMap<>();
        userRepository.findAllById(creatorIds)
                .forEach(user -> creators.put(user.getId(), toCreatorResponse(user)));
        return creators;
    }

    private EventCreatorResponse findCreator(Long creatorUserId) {
        if (creatorUserId == null) {
            return null;
        }
        return userRepository.findById(creatorUserId)
                .map(this::toCreatorResponse)
                .orElse(null);
    }

    private EventCreatorResponse toCreatorResponse(UserEntity creator) {
        return EventCreatorResponse.builder()
                .userId(creator.getUserId())
                .name(creator.getName())
                .build();
    }

    private EventResponse toResponse(EventEntity event, EventCreatorResponse creator) {
        return EventResponse.builder()
                .id(event.getId())
                .projectId(event.getProjectId())
                .title(event.getTitle())
                .content(event.getContent())
                .mode(event.getMode())
                .location(event.getLocation())
                .meetingUrl(event.getMeetingUrl())
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .status(event.getStatus())
                .visibility(event.getVisibility())
                .creator(creator)
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .deletedAt(event.getDeletedAt())
                .build();
    }

    private Map<String, Object> snapshot(EventEntity event) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("projectId", event.getProjectId());
        value.put("title", event.getTitle());
        value.put("content", event.getContent());
        value.put("mode", event.getMode().name());
        value.put("location", event.getLocation());
        value.put("meetingUrl", event.getMeetingUrl());
        value.put("startAt", event.getStartAt().toString());
        value.put("endAt", event.getEndAt() == null ? null : event.getEndAt().toString());
        value.put("status", event.getStatus().name());
        value.put("visibility", event.getVisibility().name());
        value.put("createdByUserId", event.getCreatedByUserId());
        value.put("deletedAt", event.getDeletedAt() == null ? null : event.getDeletedAt().toString());
        return value;
    }

    private ResponseStatusException eventNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
