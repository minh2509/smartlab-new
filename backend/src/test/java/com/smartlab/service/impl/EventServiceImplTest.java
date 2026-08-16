package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateEventRequest;
import com.smartlab.dto.request.UpdateEventRequest;
import com.smartlab.dto.response.EventResponse;
import com.smartlab.entity.EventEntity;
import com.smartlab.entity.ProjectEntity;
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
import com.smartlab.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {
    private static final String EMAIL = "member@smartlab.test";
    private static final Instant START = Instant.parse("2026-08-20T08:00:00Z");
    private static final Instant END = Instant.parse("2026-08-20T10:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-08-12T08:00:00Z");

    @Mock
    private EventRepository eventRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PermissionService permissionService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private EventServiceImpl service;

    @Test
    void regularListIncludesPublicLabAndOnlyActiveMemberProjectsWhilePreservingRepositoryOrder() {
        UserEntity viewer = user(11L, "member-user", EMAIL);
        authenticate(viewer, Set.of("MEMBER"));
        when(permissionService.getEffectivePermissionCodes(viewer)).thenReturn(Set.of("PROJECT_READ"));
        EventEntity visibleProject = projectEvent(1L, 7L, 21L);
        EventEntity publicEvent = labEvent(2L, EventVisibility.PUBLIC, 21L);
        EventEntity labEvent = labEvent(3L, EventVisibility.LAB, 21L);
        EventEntity hiddenProject = projectEvent(4L, 8L, 21L);
        when(eventRepository.findActiveEvents(eq(7L), eq(EventStatus.SCHEDULED), eq(true), any(Instant.class)))
                .thenReturn(List.of(visibleProject, publicEvent, labEvent, hiddenProject));
        when(projectMemberRepository.findActiveProjectIdsByUserId(11L)).thenReturn(List.of(7L));
        when(userRepository.findAllById(any())).thenReturn(List.of(user(21L, "creator-user", "creator@test")));

        List<EventResponse> result = service.list(EMAIL, 7L, EventStatus.SCHEDULED, true);

        assertThat(result).extracting(EventResponse::getId).containsExactly(1L, 2L, 3L);
        assertThat(result.getFirst().getCreator().getUserId()).isEqualTo("creator-user");
        verify(eventRepository).findActiveEvents(eq(7L), eq(EventStatus.SCHEDULED), eq(true), any(Instant.class));
    }

    @Test
    void administratorReadsEveryProjectEventWithoutMembershipLookup() {
        UserEntity admin = user(11L, "admin-user", EMAIL);
        authenticate(admin, Set.of("ADMIN"));
        when(eventRepository.findActiveEvents(eq(null), eq(null), eq(null), any(Instant.class)))
                .thenReturn(List.of(projectEvent(4L, 99L, 21L)));

        List<EventResponse> result = service.list(EMAIL, null, null, null);

        assertThat(result).extracting(EventResponse::getId).containsExactly(4L);
        assertThat(result.getFirst().getCreator()).isNull();
        verify(projectMemberRepository, never()).findActiveProjectIdsByUserId(any());
    }

    @Test
    void hiddenProjectDetailLooksNotFound() {
        UserEntity viewer = user(11L, "member-user", EMAIL);
        authenticate(viewer, Set.of("MEMBER"));
        when(permissionService.getEffectivePermissionCodes(viewer)).thenReturn(Set.of("PROJECT_READ"));
        when(eventRepository.findActiveById(4L)).thenReturn(Optional.of(projectEvent(4L, 8L, 21L)));
        when(projectMemberRepository.findActiveProjectIdsByUserId(11L)).thenReturn(List.of(7L));

        assertStatus(HttpStatus.NOT_FOUND, () -> service.get(4L, EMAIL));

        verify(userRepository, never()).findById(21L);
    }

    @Test
    void activeProjectMemberReadsProjectDetail() {
        UserEntity viewer = user(11L, "member-user", EMAIL);
        UserEntity creator = user(21L, "creator-user", "creator@test");
        authenticate(viewer, Set.of("MEMBER"));
        when(permissionService.getEffectivePermissionCodes(viewer)).thenReturn(Set.of("PROJECT_READ"));
        when(eventRepository.findActiveById(4L)).thenReturn(Optional.of(projectEvent(4L, 7L, 21L)));
        when(projectMemberRepository.findActiveProjectIdsByUserId(11L)).thenReturn(List.of(7L));
        when(userRepository.findById(21L)).thenReturn(Optional.of(creator));

        EventResponse response = service.get(4L, EMAIL);

        assertThat(response.getId()).isEqualTo(4L);
        assertThat(response.getCreator().getName()).isEqualTo("creator-user");
    }

    @Test
    void regularAccountWithoutProjectReadSeesPublicAndLabButNotProjectEvents() {
        UserEntity viewer = user(11L, "member-user", EMAIL);
        authenticate(viewer, Set.of("MEMBER"));
        when(permissionService.getEffectivePermissionCodes(viewer)).thenReturn(Set.of());
        EventEntity project = projectEvent(1L, 7L, 21L);
        EventEntity publicEvent = labEvent(2L, EventVisibility.PUBLIC, 21L);
        EventEntity labEvent = labEvent(3L, EventVisibility.LAB, 21L);
        when(eventRepository.findActiveEvents(eq(null), eq(null), eq(null), any(Instant.class)))
                .thenReturn(List.of(project, publicEvent, labEvent));
        when(userRepository.findAllById(any())).thenReturn(List.of(user(21L, "creator-user", "creator@test")));

        List<EventResponse> result = service.list(EMAIL, null, null, null);

        assertThat(result).extracting(EventResponse::getId).containsExactly(2L, 3L);
        verify(projectMemberRepository, never()).findActiveProjectIdsByUserId(any());
    }

    @Test
    void projectDetailIsConcealedWithoutEffectiveProjectReadEvenForMember() {
        UserEntity viewer = user(11L, "member-user", EMAIL);
        authenticate(viewer, Set.of("MEMBER"));
        when(permissionService.getEffectivePermissionCodes(viewer)).thenReturn(Set.of());
        when(eventRepository.findActiveById(4L)).thenReturn(Optional.of(projectEvent(4L, 7L, 21L)));

        assertStatus(HttpStatus.NOT_FOUND, () -> service.get(4L, EMAIL));

        verify(projectMemberRepository, never()).findActiveProjectIdsByUserId(any());
        verify(userRepository, never()).findById(21L);
    }

    @Test
    void adminWithProjectManageCreatesLabEventAndAuditsDefaultStatus() throws Exception {
        UserEntity admin = user(11L, "admin-user", EMAIL);
        authenticate(admin, Set.of("ADMIN"));
        when(permissionService.getEffectivePermissionCodes(admin)).thenReturn(Set.of("PROJECT_MANAGE"));
        when(eventRepository.saveAndFlush(any(EventEntity.class))).thenAnswer(invocation -> {
            EventEntity event = invocation.getArgument(0);
            setId(event, 50L);
            return event;
        });
        CreateEventRequest request = inPersonCreate(null, EventVisibility.LAB);
        request.setTitle("  Lab meetup  ");
        request.setContent(null);
        request.setEndAt(null);

        EventResponse response = service.create(request, EMAIL);

        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response.getTitle()).isEqualTo("Lab meetup");
        assertThat(response.getContent()).isNull();
        assertThat(response.getEndAt()).isNull();
        assertThat(response.getStatus()).isEqualTo(EventStatus.SCHEDULED);
        assertThat(response.getCreator().getUserId()).isEqualTo("admin-user");
        verify(auditService).log(eq("EVENT_CREATED"), eq("EVENT"), eq("50"), eq(null), any(Map.class));
    }

    @Test
    void nonAdminCannotUseGlobalProjectManageToCreateLabEvent() {
        UserEntity member = user(11L, "member-user", EMAIL);
        authenticate(member, Set.of("MEMBER"));

        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.create(inPersonCreate(null, EventVisibility.LAB), EMAIL));

        verify(eventRepository, never()).saveAndFlush(any());
        verify(permissionService, never()).getEffectivePermissionCodes(member);
    }

    @Test
    void activeProjectLeaderCreatesEventOnlyForTheirProject() throws Exception {
        UserEntity leader = user(11L, "leader-user", EMAIL);
        authenticate(leader, Set.of("MEMBER"));
        when(projectRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(org.mockito.Mockito.mock(ProjectEntity.class)));
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                7L, 11L, ProjectRole.LEADER, ProjectMemberStatus.ACTIVE
        )).thenReturn(true);
        when(eventRepository.saveAndFlush(any(EventEntity.class))).thenAnswer(invocation -> {
            EventEntity event = invocation.getArgument(0);
            setId(event, 51L);
            return event;
        });

        EventResponse response = service.create(inPersonCreate(7L, EventVisibility.PROJECT), EMAIL);

        assertThat(response.getProjectId()).isEqualTo(7L);
        assertThat(response.getVisibility()).isEqualTo(EventVisibility.PROJECT);
        verify(auditService).log(eq("EVENT_CREATED"), eq("EVENT"), eq("51"), eq(null), any(Map.class));
    }

    @Test
    void leaderCannotCreateForAnotherProject() {
        UserEntity leader = user(11L, "leader-user", EMAIL);
        authenticate(leader, Set.of("MEMBER"));
        when(projectRepository.findByIdAndDeletedAtIsNull(8L)).thenReturn(Optional.of(org.mockito.Mockito.mock(ProjectEntity.class)));

        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.create(inPersonCreate(8L, EventVisibility.PROJECT), EMAIL));

        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void projectLeaderCannotCreatePublicOrLabScopedEventEvenForOwnProject() {
        UserEntity leader = user(11L, "leader-user", EMAIL);
        authenticate(leader, Set.of("MEMBER"));
        when(projectRepository.findByIdAndDeletedAtIsNull(7L))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(ProjectEntity.class)));

        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.create(inPersonCreate(7L, EventVisibility.LAB), EMAIL));
        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.create(inPersonCreate(7L, EventVisibility.PUBLIC), EMAIL));

        verify(projectMemberRepository, never())
                .existsByProject_IdAndUser_IdAndProjectRoleAndStatus(any(), any(), any(), any());
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void administratorMayCreateProjectLinkedPublicEvent() throws Exception {
        UserEntity admin = user(11L, "admin-user", EMAIL);
        authenticate(admin, Set.of("ADMIN"));
        when(permissionService.getEffectivePermissionCodes(admin)).thenReturn(Set.of("PROJECT_MANAGE"));
        when(projectRepository.findByIdAndDeletedAtIsNull(7L))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(ProjectEntity.class)));
        when(eventRepository.saveAndFlush(any(EventEntity.class))).thenAnswer(invocation -> {
            EventEntity event = invocation.getArgument(0);
            setId(event, 52L);
            return event;
        });

        EventResponse response = service.create(inPersonCreate(7L, EventVisibility.PUBLIC), EMAIL);

        assertThat(response.getProjectId()).isEqualTo(7L);
        assertThat(response.getVisibility()).isEqualTo(EventVisibility.PUBLIC);
    }

    @Test
    void validatesAbsoluteHttpUrlTimeAndVisibilityInService() {
        UserEntity admin = user(11L, "admin-user", EMAIL);
        authenticate(admin, Set.of("ADMIN"));

        CreateEventRequest invalidUrl = onlineCreate();
        invalidUrl.setMeetingUrl("ftp://meet.example/demo");
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(invalidUrl, EMAIL));

        CreateEventRequest invalidTime = inPersonCreate(null, EventVisibility.LAB);
        invalidTime.setEndAt(START);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(invalidTime, EMAIL));

        CreateEventRequest inconsistent = inPersonCreate(null, EventVisibility.PROJECT);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(inconsistent, EMAIL));

        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void patchRejectsAnyProjectIdPropertyBeforeLookingUpEvent() {
        UpdateEventRequest request = new UpdateEventRequest();
        request.setProjectId(null);

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.update(4L, request, EMAIL));

        verify(eventRepository, never()).findActiveByIdForUpdate(any());
    }

    @Test
    void patchRejectsEmptyBodyWithoutLookupOrAudit() {
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.update(4L, new UpdateEventRequest(), EMAIL));

        verify(eventRepository, never()).findActiveByIdForUpdate(any());
        verify(auditService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void patchExplicitNullClearsOptionalContentAndEndTime() {
        UserEntity admin = user(11L, "admin-user", EMAIL);
        authenticate(admin, Set.of("ADMIN"));
        when(permissionService.getEffectivePermissionCodes(admin)).thenReturn(Set.of("PROJECT_MANAGE"));
        EventEntity event = labEvent(4L, EventVisibility.LAB, 21L);
        when(eventRepository.findActiveByIdForUpdate(4L)).thenReturn(Optional.of(event));
        when(eventRepository.saveAndFlush(event)).thenReturn(event);
        UpdateEventRequest request = new UpdateEventRequest();
        request.setContent(null);
        request.setEndAt(null);

        EventResponse response = service.update(4L, request, EMAIL);

        assertThat(response.getContent()).isNull();
        assertThat(response.getEndAt()).isNull();
        verify(auditService).log(eq("EVENT_UPDATED"), eq("EVENT"), eq("4"), any(Map.class), any(Map.class));
    }

    @Test
    void patchWithAnUnchangedValueDoesNotWriteOrAudit() {
        UserEntity admin = user(11L, "admin-user", EMAIL);
        authenticate(admin, Set.of("ADMIN"));
        when(permissionService.getEffectivePermissionCodes(admin)).thenReturn(Set.of("PROJECT_MANAGE"));
        EventEntity event = labEvent(4L, EventVisibility.LAB, 21L);
        when(eventRepository.findActiveByIdForUpdate(4L)).thenReturn(Optional.of(event));
        when(userRepository.findById(21L)).thenReturn(Optional.of(user(21L, "creator", "creator@test")));
        UpdateEventRequest request = new UpdateEventRequest();
        request.setTitle("  Lab meetup  ");

        EventResponse response = service.update(4L, request, EMAIL);

        assertThat(response.getTitle()).isEqualTo("Lab meetup");
        assertThat(response.getUpdatedAt()).isEqualTo(CREATED);
        verify(eventRepository, never()).saveAndFlush(any());
        verify(auditService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void projectLeaderUpdatesOwnEventSwitchesModeAndAudits() {
        UserEntity leader = user(11L, "leader-user", EMAIL);
        authenticate(leader, Set.of("MEMBER"));
        EventEntity event = projectEvent(4L, 7L, 21L);
        when(eventRepository.findActiveByIdForUpdate(4L)).thenReturn(Optional.of(event));
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                7L, 11L, ProjectRole.LEADER, ProjectMemberStatus.ACTIVE
        )).thenReturn(true);
        when(projectRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(org.mockito.Mockito.mock(ProjectEntity.class)));
        when(eventRepository.saveAndFlush(event)).thenReturn(event);
        when(userRepository.findById(21L)).thenReturn(Optional.of(user(21L, "creator", "creator@test")));
        UpdateEventRequest request = new UpdateEventRequest();
        request.setTitle("  Online demo  ");
        request.setMode(EventMode.ONLINE);
        request.setMeetingUrl("https://meet.example/new");
        request.setStatus(EventStatus.COMPLETED);

        EventResponse response = service.update(4L, request, EMAIL);

        assertThat(response.getTitle()).isEqualTo("Online demo");
        assertThat(response.getMode()).isEqualTo(EventMode.ONLINE);
        assertThat(response.getLocation()).isNull();
        assertThat(response.getMeetingUrl()).isEqualTo("https://meet.example/new");
        assertThat(response.getStatus()).isEqualTo(EventStatus.COMPLETED);
        assertThat(response.getProjectId()).isEqualTo(7L);
        verify(auditService).log(eq("EVENT_UPDATED"), eq("EVENT"), eq("4"), any(Map.class), any(Map.class));
    }

    @Test
    void projectLeaderCannotPromoteOwnProjectEventToLabOrPublicVisibility() {
        UserEntity leader = user(11L, "leader-user", EMAIL);
        authenticate(leader, Set.of("MEMBER"));
        EventEntity event = projectEvent(4L, 7L, 21L);
        when(eventRepository.findActiveByIdForUpdate(4L)).thenReturn(Optional.of(event));
        when(projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                7L, 11L, ProjectRole.LEADER, ProjectMemberStatus.ACTIVE
        )).thenReturn(true);

        UpdateEventRequest labRequest = new UpdateEventRequest();
        labRequest.setVisibility(EventVisibility.LAB);
        assertStatus(HttpStatus.FORBIDDEN, () -> service.update(4L, labRequest, EMAIL));

        UpdateEventRequest publicRequest = new UpdateEventRequest();
        publicRequest.setVisibility(EventVisibility.PUBLIC);
        assertStatus(HttpStatus.FORBIDDEN, () -> service.update(4L, publicRequest, EMAIL));

        assertThat(event.getVisibility()).isEqualTo(EventVisibility.PROJECT);
        verify(eventRepository, never()).saveAndFlush(any());
        verify(auditService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void patchCannotChangeVisibilityAcrossImmutableProjectBoundary() {
        UserEntity admin = user(11L, "admin-user", EMAIL);
        authenticate(admin, Set.of("ADMIN"));
        when(permissionService.getEffectivePermissionCodes(admin)).thenReturn(Set.of("PROJECT_MANAGE"));
        EventEntity event = labEvent(4L, EventVisibility.LAB, 21L);
        when(eventRepository.findActiveByIdForUpdate(4L)).thenReturn(Optional.of(event));
        UpdateEventRequest request = new UpdateEventRequest();
        request.setVisibility(EventVisibility.PROJECT);

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.update(4L, request, EMAIL));

        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void projectLeaderCannotMutateLabEvent() {
        UserEntity leader = user(11L, "leader-user", EMAIL);
        authenticate(leader, Set.of("MEMBER"));
        when(eventRepository.findActiveByIdForUpdate(4L))
                .thenReturn(Optional.of(labEvent(4L, EventVisibility.LAB, 21L)));

        UpdateEventRequest request = new UpdateEventRequest();
        request.setTitle("Updated");
        assertStatus(HttpStatus.FORBIDDEN, () -> service.update(4L, request, EMAIL));

        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void adminSoftDeletesAndAuditsEvent() {
        UserEntity admin = user(11L, "admin-user", EMAIL);
        authenticate(admin, Set.of("ADMIN"));
        when(permissionService.getEffectivePermissionCodes(admin)).thenReturn(Set.of("PROJECT_MANAGE"));
        EventEntity event = labEvent(4L, EventVisibility.LAB, 21L);
        when(eventRepository.findActiveByIdForUpdate(4L)).thenReturn(Optional.of(event));
        when(eventRepository.saveAndFlush(event)).thenReturn(event);

        service.delete(4L, EMAIL);

        assertThat(event.getDeletedAt()).isNotNull();
        assertThat(event.getUpdatedAt()).isEqualTo(event.getDeletedAt());
        verify(auditService).log(eq("EVENT_DELETED"), eq("EVENT"), eq("4"), any(Map.class), any(Map.class));
    }

    @Test
    void rejectsAnonymousAndMissingEventsWithContractStatuses() {
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.list(null, null, null, null));

        UserEntity viewer = user(11L, "member-user", EMAIL);
        authenticate(viewer, Set.of("MEMBER"));
        when(eventRepository.findActiveById(404L)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.get(404L, EMAIL));
    }

    private void authenticate(UserEntity user, Set<String> roleCodes) {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(permissionService.hasInactiveAssignedRole(user)).thenReturn(false);
        org.mockito.Mockito.lenient().when(permissionService.getRoleCodes(user)).thenReturn(roleCodes);
    }

    private static CreateEventRequest inPersonCreate(Long projectId, EventVisibility visibility) {
        CreateEventRequest request = new CreateEventRequest();
        request.setProjectId(projectId);
        request.setTitle("Lab meetup");
        request.setContent("Content");
        request.setMode(EventMode.IN_PERSON);
        request.setLocation("Room A");
        request.setStartAt(START);
        request.setEndAt(END);
        request.setVisibility(visibility);
        return request;
    }

    private static CreateEventRequest onlineCreate() {
        CreateEventRequest request = new CreateEventRequest();
        request.setTitle("Online demo");
        request.setMode(EventMode.ONLINE);
        request.setMeetingUrl("https://meet.example/demo");
        request.setStartAt(START);
        request.setEndAt(END);
        request.setVisibility(EventVisibility.LAB);
        return request;
    }

    private static EventEntity labEvent(Long id, EventVisibility visibility, Long creatorId) {
        EventEntity event = EventEntity.create(
                null, "Lab meetup", "Content", EventMode.IN_PERSON, "Room A", null,
                START, END, EventStatus.SCHEDULED, visibility, creatorId, CREATED
        );
        setId(event, id);
        return event;
    }

    private static EventEntity projectEvent(Long id, Long projectId, Long creatorId) {
        EventEntity event = EventEntity.create(
                projectId, "Project demo", "Content", EventMode.IN_PERSON, "Room P", null,
                START, END, EventStatus.SCHEDULED, EventVisibility.PROJECT, creatorId, CREATED
        );
        setId(event, id);
        return event;
    }

    private static UserEntity user(Long id, String userId, String email) {
        return UserEntity.builder()
                .id(id)
                .userId(userId)
                .name(userId)
                .email(email)
                .isActive(true)
                .isAccountVerified(true)
                .build();
    }

    private static void setId(EventEntity event, Long id) {
        try {
            Field field = EventEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(event, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void assertStatus(HttpStatus status, ThrowingAction action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(status));
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run();
    }
}
