package com.smartlab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationEntityMappingTest {
    private static final Instant TIME = Instant.parse("2026-08-10T10:00:00Z");

    @Test
    void mapsExactReconciledSchemaWithScalarIds() {
        assertThat(Arrays.stream(NotificationEntity.class.getDeclaredFields()).map(Field::getName))
                .containsExactlyInAnyOrder("id", "recipientUserId", "actorUserId", "type", "message",
                        "relatedType", "relatedId", "targetUrl", "isRead", "deletedAt", "createdAt");
        assertColumn("recipientUserId", "recipient_user_id", 255);
        assertColumn("actorUserId", "actor_user_id", 255);
        assertColumn("type", "type", 100);
        assertColumn("message", "message", 1000);
        assertColumn("relatedType", "related_type", 80);
        assertColumn("relatedId", "related_id", 255);
        assertColumn("targetUrl", "target_url", 500);
        assertColumn("isRead", "is_read", 255);
        assertColumn("deletedAt", "deleted_at", 255);
        assertColumn("createdAt", "created_at", 255);
        assertThat(Arrays.stream(NotificationEntity.class.getDeclaredFields()).noneMatch(f -> f.isAnnotationPresent(ManyToOne.class))).isTrue();
    }

    @Test
    void factoryPreservesExactInputsAndStartsUnreadUndeleted() {
        NotificationEntity n = NotificationEntity.create(9L, 8L, " TYPE ", " message ", " POST ", 7L, " /posts/7 ", TIME);
        assertThat(n.getRecipientUserId()).isEqualTo(9L);
        assertThat(n.getActorUserId()).isEqualTo(8L);
        assertThat(n.getType()).isEqualTo(" TYPE ");
        assertThat(n.getMessage()).isEqualTo(" message ");
        assertThat(n.getRelatedType()).isEqualTo(" POST ");
        assertThat(n.getRelatedId()).isEqualTo(7L);
        assertThat(n.getTargetUrl()).isEqualTo(" /posts/7 ");
        assertThat(n.isRead()).isFalse();
        assertThat(n.getDeletedAt()).isNull();
        assertThat(n.getCreatedAt()).isEqualTo(TIME);
    }

    @Test
    void acceptsExactColumnBoundariesAndNullableMetadata() {
        NotificationEntity n = NotificationEntity.create(1L, null, "t".repeat(100), "m".repeat(1000),
                "r".repeat(80), null, "u".repeat(500), TIME);
        assertThat(n.getMessage()).hasSize(1000);
        assertThat(n.getActorUserId()).isNull();
        assertThat(n.getRelatedId()).isNull();
    }

    @Test
    void rejectsInvalidContractValues() {
        assertThatThrownBy(() -> NotificationEntity.create(0L, null, "TYPE", "m", null, null, null, TIME)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationEntity.create(1L, 0L, "TYPE", "m", null, null, null, TIME)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationEntity.create(1L, null, " ", "m", null, null, null, TIME)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationEntity.create(1L, null, "TYPE", "", null, null, null, TIME)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationEntity.create(1L, null, "TYPE", "   ", null, null, null, TIME)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationEntity.create(1L, null, "t".repeat(101), "m", null, null, null, TIME)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationEntity.create(1L, null, "TYPE", "m".repeat(1001), null, null, null, TIME)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationEntity.create(1L, null, "TYPE", "m", "r".repeat(81), null, null, TIME)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationEntity.create(1L, null, "TYPE", "m", null, 0L, null, TIME)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationEntity.create(1L, null, "TYPE", "m", null, null, "u".repeat(501), TIME)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void controlledOperationsChangeOnlyReadAndDeletedState() {
        NotificationEntity n = NotificationEntity.create(1L, null, "TYPE", "m", null, null, null, TIME);
        n.markRead();
        n.softDelete(TIME.plusSeconds(1));
        assertThat(n.isRead()).isTrue();
        assertThat(n.getDeletedAt()).isEqualTo(TIME.plusSeconds(1));
    }

    private static void assertColumn(String fieldName, String columnName, int length) {
        try {
            Column column = NotificationEntity.class.getDeclaredField(fieldName).getAnnotation(Column.class);
            assertThat(column.name()).isEqualTo(columnName);
            assertThat(column.length()).isEqualTo(length);
        } catch (NoSuchFieldException e) { throw new AssertionError(e); }
    }
}
