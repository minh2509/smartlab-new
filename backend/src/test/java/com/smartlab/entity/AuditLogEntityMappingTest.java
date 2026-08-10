package com.smartlab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditLogEntityMappingTest {
    @Test
    void mapsExactRuntimeContractAndDatabaseGeneratedTimestamp() throws Exception {
        assertThat(AuditLogEntity.class.getAnnotation(Table.class).name()).isEqualTo("audit_logs");
        assertThat(Arrays.stream(AuditLogEntity.class.getDeclaredFields()).map(Field::getName))
                .containsExactlyInAnyOrder("id", "actorUserId", "action", "targetType", "targetId",
                        "beforeJson", "afterJson", "ipAddress", "userAgent", "createdAt");
        assertColumn("action", "action", 120, false);
        assertColumn("targetType", "target_type", 80, false);
        assertColumn("targetId", "target_id", 80, true);
        assertColumn("ipAddress", "ip_address", 45, true);
        assertThat(field("actorUserId").getType()).isEqualTo(Long.class);
        assertThat(field("beforeJson").getAnnotation(JdbcTypeCode.class).value()).isEqualTo(SqlTypes.JSON);
        assertThat(field("afterJson").getAnnotation(JdbcTypeCode.class).value()).isEqualTo(SqlTypes.JSON);
        assertThat(field("userAgent").getAnnotation(Column.class).columnDefinition()).isEqualTo("TEXT");
        Column createdAt = field("createdAt").getAnnotation(Column.class);
        assertThat(createdAt.insertable()).isFalse();
        assertThat(createdAt.updatable()).isFalse();
    }

    @Test
    void createsValidatedImmutableAppendOnlyRecordWithoutTimestamp() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("nested", new LinkedHashMap<>(Map.of("status", "OLD")));
        AuditLogEntity audit = AuditLogEntity.create(7L, "ROLE_UPDATED", "ROLE", "9", before,
                Map.of("status", "NEW"), "127.0.0.1", "agent");
        ((Map<String, Object>) before.get("nested")).put("status", "MUTATED");

        assertThat(audit.getActorUserId()).isEqualTo(7L);
        assertThat(audit.getBeforeJson()).isEqualTo(Map.of("nested", Map.of("status", "OLD")));
        assertThat(audit.getCreatedAt()).isNull();
        assertThatThrownBy(() -> audit.getBeforeJson().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(Arrays.stream(AuditLogEntity.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("set") || name.toLowerCase().contains("delete") || name.toLowerCase().contains("update"));
    }

    @Test
    void enforcesBoundariesAndAllowsNullableContextAndSnapshots() {
        AuditLogEntity audit = AuditLogEntity.create(null, "a".repeat(120), "t".repeat(80),
                "i".repeat(80), null, null, null, null);
        assertThat(audit.getActorUserId()).isNull();
        assertThatThrownBy(() -> AuditLogEntity.create(0L, "A", "T", null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditLogEntity.create(null, " ", "T", null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditLogEntity.create(null, "a".repeat(121), "T", null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditLogEntity.create(null, "A", " ", null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditLogEntity.create(null, "A", "t".repeat(81), null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditLogEntity.create(null, "A", "T", "i".repeat(81), null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditLogEntity.create(null, "A", "T", null, null, null, "i".repeat(46), null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Field field(String name) throws Exception { return AuditLogEntity.class.getDeclaredField(name); }
    private static void assertColumn(String field, String name, int length, boolean nullable) throws Exception {
        Column column = field(field).getAnnotation(Column.class);
        assertThat(column.name()).isEqualTo(name);
        assertThat(column.length()).isEqualTo(length);
        assertThat(column.nullable()).isEqualTo(nullable);
    }
}
