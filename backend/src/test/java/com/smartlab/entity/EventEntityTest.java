package com.smartlab.entity;

import com.smartlab.enums.EventMode;
import com.smartlab.enums.EventStatus;
import com.smartlab.enums.EventVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventEntityTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-12T08:00:00Z");
    private static final Instant START_AT = Instant.parse("2026-08-20T08:00:00Z");
    private static final Instant END_AT = Instant.parse("2026-08-20T10:00:00Z");

    @Test
    void mapsApprovedEventsTableAndScalarReferences() throws Exception {
        assertThat(EventEntity.class.isAnnotationPresent(Entity.class)).isTrue();
        assertThat(EventEntity.class.getAnnotation(Table.class).name()).isEqualTo("events");
        assertThat(fieldNames()).containsExactlyInAnyOrder(
                "id", "projectId", "title", "content", "mode", "location", "meetingUrl",
                "startAt", "endAt", "status", "visibility", "createdByUserId",
                "createdAt", "updatedAt", "deletedAt"
        );

        Field id = field("id");
        assertThat(id.isAnnotationPresent(Id.class)).isTrue();
        assertThat(id.getAnnotation(GeneratedValue.class).strategy()).isEqualTo(GenerationType.IDENTITY);
        assertColumn("content", "description", true, 255);
        assertColumn("meetingUrl", "meeting_url", true, 2048);
        assertColumn("endAt", "end_at", true, 255);
        assertColumn("deletedAt", "deleted_at", true, 255);
        assertEnum("mode", EventMode.class);
        assertEnum("status", EventStatus.class);
        assertEnum("visibility", EventVisibility.class);
        assertThat(Arrays.stream(EventEntity.class.getDeclaredFields()))
                .noneMatch(field -> field.isAnnotationPresent(ManyToOne.class)
                        || field.isAnnotationPresent(OneToOne.class)
                        || field.isAnnotationPresent(OneToMany.class)
                        || field.isAnnotationPresent(ManyToMany.class));
    }

    @Test
    void hasProtectedJpaConstructorAndNoBroadSetters() throws Exception {
        Constructor<EventEntity> constructor = EventEntity.class.getDeclaredConstructor();
        assertThat(Modifier.isProtected(constructor.getModifiers())).isTrue();
        assertThat(Arrays.stream(EventEntity.class.getDeclaredMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("set"));
    }

    @Test
    void createsOnlineEventWithOptionalContentAndEndTime() {
        EventEntity event = EventEntity.create(
                7L, "Demo", null, EventMode.ONLINE, null, "https://meet.example/demo",
                START_AT, null, EventStatus.SCHEDULED, EventVisibility.PROJECT, 11L, CREATED_AT
        );

        assertThat(event.getProjectId()).isEqualTo(7L);
        assertThat(event.getContent()).isNull();
        assertThat(event.getEndAt()).isNull();
        assertThat(event.getCreatedByUserId()).isEqualTo(11L);
        assertThat(event.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(event.getUpdatedAt()).isEqualTo(CREATED_AT);
        assertThat(event.getDeletedAt()).isNull();
    }

    @Test
    void updateChangesOnlyMutableFields() {
        EventEntity event = labEvent();
        Instant mutationTime = CREATED_AT.plusSeconds(60);

        event.applyUpdate(
                "Updated", null, EventMode.ONLINE, null, "https://meet.example/new",
                START_AT.plusSeconds(60), null, EventStatus.COMPLETED,
                EventVisibility.PUBLIC, mutationTime
        );

        assertThat(event.getTitle()).isEqualTo("Updated");
        assertThat(event.getMode()).isEqualTo(EventMode.ONLINE);
        assertThat(event.getLocation()).isNull();
        assertThat(event.getMeetingUrl()).isEqualTo("https://meet.example/new");
        assertThat(event.getStatus()).isEqualTo(EventStatus.COMPLETED);
        assertThat(event.getVisibility()).isEqualTo(EventVisibility.PUBLIC);
        assertThat(event.getProjectId()).isNull();
        assertThat(event.getCreatedByUserId()).isEqualTo(11L);
        assertThat(event.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(event.getUpdatedAt()).isEqualTo(mutationTime);
    }

    @Test
    void rejectsInvalidModeFieldsTimeAndProjectVisibility() {
        assertThatThrownBy(() -> EventEntity.create(
                null, "Demo", null, EventMode.ONLINE, "Room", null,
                START_AT, END_AT, EventStatus.SCHEDULED, EventVisibility.LAB, 11L, CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> EventEntity.create(
                null, "Demo", null, EventMode.IN_PERSON, "Room", null,
                START_AT, START_AT, EventStatus.SCHEDULED, EventVisibility.LAB, 11L, CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after start");

        assertThatThrownBy(() -> EventEntity.create(
                null, "Demo", null, EventMode.IN_PERSON, "Room", null,
                START_AT, END_AT, EventStatus.SCHEDULED, EventVisibility.PROJECT, 11L, CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inconsistent");
    }

    @Test
    void softDeleteUsesOneMutationTimestampAndPreservesData() {
        EventEntity event = labEvent();
        Instant deletedAt = CREATED_AT.plusSeconds(120);

        event.softDelete(deletedAt);

        assertThat(event.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(event.getUpdatedAt()).isEqualTo(deletedAt);
        assertThat(event.getTitle()).isEqualTo("Lab meetup");
        assertThat(event.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    private static EventEntity labEvent() {
        return EventEntity.create(
                null, "Lab meetup", "Content", EventMode.IN_PERSON, "Room A", null,
                START_AT, END_AT, EventStatus.SCHEDULED, EventVisibility.LAB, 11L, CREATED_AT
        );
    }

    private static Set<String> fieldNames() {
        return Arrays.stream(EventEntity.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
    }

    private static Field field(String name) throws NoSuchFieldException {
        return EventEntity.class.getDeclaredField(name);
    }

    private static void assertColumn(String fieldName, String columnName, boolean nullable, int length)
            throws NoSuchFieldException {
        Column column = field(fieldName).getAnnotation(Column.class);
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.nullable()).isEqualTo(nullable);
        assertThat(column.length()).isEqualTo(length);
    }

    private static void assertEnum(String fieldName, Class<?> type) throws NoSuchFieldException {
        Field field = field(fieldName);
        assertThat(field.getType()).isEqualTo(type);
        assertThat(field.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
    }
}
