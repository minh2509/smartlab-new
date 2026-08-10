package com.smartlab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "audit_logs")
public class AuditLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "action", nullable = false, length = 120)
    private String action;

    @Column(name = "target_type", nullable = false, length = 80)
    private String targetType;

    @Column(name = "target_id", length = 80)
    private String targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json", columnDefinition = "jsonb")
    private Map<String, Object> beforeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json", columnDefinition = "jsonb")
    private Map<String, Object> afterJson;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public static AuditLogEntity create(
            Long actorUserId,
            String action,
            String targetType,
            String targetId,
            Map<String, Object> beforeJson,
            Map<String, Object> afterJson,
            String ipAddress,
            String userAgent
    ) {
        requirePositiveOptional(actorUserId, "Audit actor user ID");
        requireText(action, 120, "Audit action");
        requireText(targetType, 80, "Audit target type");
        validateOptionalLength(targetId, 80, "Audit target ID");
        validateOptionalLength(ipAddress, 45, "Audit IP address");

        AuditLogEntity audit = new AuditLogEntity();
        audit.actorUserId = actorUserId;
        audit.action = action;
        audit.targetType = targetType;
        audit.targetId = targetId;
        audit.beforeJson = copyJsonObject(beforeJson);
        audit.afterJson = copyJsonObject(afterJson);
        audit.ipAddress = ipAddress;
        audit.userAgent = userAgent;
        return audit;
    }

    private static void requirePositiveOptional(Long value, String label) {
        if (value != null && value <= 0) throw new IllegalArgumentException(label + " must be positive");
    }

    private static void requireText(String value, int maximum, String label) {
        Objects.requireNonNull(value, label + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        if (value.length() > maximum) throw new IllegalArgumentException(label + " must be at most " + maximum + " characters");
    }

    private static void validateOptionalLength(String value, int maximum, String label) {
        if (value != null && value.length() > maximum) {
            throw new IllegalArgumentException(label + " must be at most " + maximum + " characters");
        }
    }

    private static Map<String, Object> copyJsonObject(Map<String, Object> source) {
        if (source == null) return null;
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(Objects.requireNonNull(key, "Audit JSON keys are required"), copyJsonValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object copyJsonValue(Object value) {
        if (value instanceof Map<?, ?> nested) {
            Map<String, Object> copy = new LinkedHashMap<>();
            nested.forEach((key, nestedValue) -> {
                if (!(key instanceof String stringKey)) throw new IllegalArgumentException("Audit JSON keys must be strings");
                copy.put(stringKey, copyJsonValue(nestedValue));
            });
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(copyJsonValue(item)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
