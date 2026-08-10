package com.smartlab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationEntityMappingTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-10T10:00:00Z");

    @Test
    void mapsTheAppliedNotificationsTableAndFields() {
        assertThat(NotificationEntity.class.isAnnotationPresent(Entity.class)).isTrue();
        assertThat(NotificationEntity.class.getAnnotation(Table.class).name()).isEqualTo("notifications");
        assertThat(fieldNames()).containsExactlyInAnyOrder(
                "id",
                "recipientUserId",
                "message",
                "linkUrl",
                "isRead",
                "createdAt"
        );
    }

    @Test
    void mapsIdentityAndScalarRecipientForeignKey() throws NoSuchFieldException {
        Field id = field("id");

        assertThat(id.isAnnotationPresent(Id.class)).isTrue();
        assertThat(id.isAnnotationPresent(GeneratedValue.class)).isTrue();
        assertThat(id.getAnnotation(GeneratedValue.class).strategy()).isEqualTo(GenerationType.IDENTITY);
        assertThat(id.getType()).isEqualTo(Long.class);
        assertColumn("recipientUserId", "recipient_user_id", false, 255);
        assertThat(field("recipientUserId").getType()).isEqualTo(Long.class);
    }

    @Test
    void mapsMessageLinkReadStateAndCreationTimestamp() throws NoSuchFieldException {
        assertColumn("message", "message", false, 500);
        assertColumn("linkUrl", "link_url", true, 500);
        assertColumn("isRead", "is_read", false, 255);
        assertColumn("createdAt", "created_at", false, 255);
        assertThat(field("isRead").getType()).isEqualTo(boolean.class);
        assertThat(field("createdAt").getType()).isEqualTo(Instant.class);
    }

    @Test
    void usesNoObjectGraphRelationships() {
        assertThat(Arrays.stream(NotificationEntity.class.getDeclaredFields()))
                .noneMatch(field -> field.isAnnotationPresent(ManyToOne.class)
                        || field.isAnnotationPresent(OneToOne.class)
                        || field.isAnnotationPresent(OneToMany.class));
    }

    @Test
    void hasProtectedJpaConstructor() throws Exception {
        Constructor<NotificationEntity> constructor = NotificationEntity.class.getDeclaredConstructor();

        assertThat(Modifier.isProtected(constructor.getModifiers())).isTrue();
    }

    @Test
    void createsUnreadNotificationPreservingSuppliedTextAndTimestamp() {
        String message = "  Your notification  ";
        String linkUrl = "  /posts/example  ";

        NotificationEntity notification = NotificationEntity.create(9L, message, linkUrl, CREATED_AT);

        assertThat(notification.getId()).isNull();
        assertThat(notification.getRecipientUserId()).isEqualTo(9L);
        assertThat(notification.getMessage()).isEqualTo(message);
        assertThat(notification.getLinkUrl()).isEqualTo(linkUrl);
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void allowsNullLinkAndBlankMessageWithoutRewritingSchemaPermittedText() {
        NotificationEntity notification = NotificationEntity.create(9L, " ", null, CREATED_AT);

        assertThat(notification.getMessage()).isEqualTo(" ");
        assertThat(notification.getLinkUrl()).isNull();
        assertThat(notification.isRead()).isFalse();
    }

    @Test
    void acceptsExactFiveHundredCharacterMessageAndLink() {
        NotificationEntity notification = NotificationEntity.create(9L, "m".repeat(500), "l".repeat(500), CREATED_AT);

        assertThat(notification.getMessage()).hasSize(500);
        assertThat(notification.getLinkUrl()).hasSize(500);
    }

    @Test
    void rejectsTextLongerThanAppliedColumnLimits() {
        assertThatThrownBy(() -> NotificationEntity.create(9L, "m".repeat(501), null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification message must be at most 500 characters");
        assertThatThrownBy(() -> NotificationEntity.create(9L, "message", "l".repeat(501), CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification link URL must be at most 500 characters");
    }

    @Test
    void rejectsMissingRequiredCreationValuesAndNonPositiveRecipients() {
        assertThatThrownBy(() -> NotificationEntity.create(null, "message", null, CREATED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Notification recipient user ID is required");
        assertThatThrownBy(() -> NotificationEntity.create(0L, "message", null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification recipient user ID must be positive");
        assertThatThrownBy(() -> NotificationEntity.create(-1L, "message", null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification recipient user ID must be positive");
        assertThatThrownBy(() -> NotificationEntity.create(9L, null, null, CREATED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Notification message is required");
        assertThatThrownBy(() -> NotificationEntity.create(9L, "message", null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Notification creation instant is required");
    }

    private static Set<String> fieldNames() {
        return Arrays.stream(NotificationEntity.class.getDeclaredFields())
                .map(Field::getName)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static Field field(String name) throws NoSuchFieldException {
        return NotificationEntity.class.getDeclaredField(name);
    }

    private static void assertColumn(String fieldName, String columnName, boolean nullable, int length)
            throws NoSuchFieldException {
        Column column = field(fieldName).getAnnotation(Column.class);

        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.nullable()).isEqualTo(nullable);
        assertThat(column.length()).isEqualTo(length);
    }
}
