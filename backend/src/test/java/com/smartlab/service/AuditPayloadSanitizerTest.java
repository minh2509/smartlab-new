package com.smartlab.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditPayloadSanitizerTest {

    @Test
    void preservesNullAndOrdinaryPayloadData() {
        Map<String, Object> payload = Map.of(
                "id", 7L,
                "status", "ACTIVE",
                "roleCodes", List.of("LEADER"),
                "document", Map.of("title", "Roadmap")
        );

        assertThat(AuditPayloadSanitizer.sanitize(null)).isNull();
        assertThat(AuditPayloadSanitizer.sanitize(payload)).isEqualTo(payload);
    }

    @Test
    void redactsRootSensitiveKeysAndCommonCompoundsCaseInsensitively() {
        Map<String, Object> payload = new LinkedHashMap<>();
        List<String> keys = List.of(
                "password", "token", "secret", "credential", "authorization", "meetingUrl",
                "refreshToken", "tokenHash", "clientSecret", "PASSWORD", "Authorization", "MeetingUrl"
        );
        keys.forEach(key -> payload.put(key, "sensitive-value"));

        assertThat(AuditPayloadSanitizer.sanitize(payload))
                .containsOnlyKeys(keys.toArray(String[]::new))
                .allSatisfy((key, value) -> assertThat(value).isEqualTo(AuditPayloadSanitizer.REDACTED));
    }

    @Test
    void sanitizesNestedMapsListsAndBinaryValuesWithoutChangingStructure() {
        byte[] rootBinary = {1, 2};
        byte[] nestedBinary = {3};
        byte[] listBinary = {4};
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("binary", rootBinary);
        payload.put("nested", Map.of("accessToken", "secret", "bytes", nestedBinary, "status", "ACTIVE"));
        payload.put("items", List.of(Map.of("rawInviteToken", "invite"), listBinary, "safe"));

        Map<String, Object> sanitized = AuditPayloadSanitizer.sanitize(payload);

        assertThat(sanitized).containsEntry("binary", AuditPayloadSanitizer.BINARY_REDACTED);
        assertThat((Map<String, Object>) sanitized.get("nested"))
                .containsEntry("accessToken", AuditPayloadSanitizer.REDACTED)
                .containsEntry("bytes", AuditPayloadSanitizer.BINARY_REDACTED)
                .containsEntry("status", "ACTIVE");
        assertThat((List<Object>) sanitized.get("items")).containsExactly(
                Map.of("rawInviteToken", AuditPayloadSanitizer.REDACTED),
                AuditPayloadSanitizer.BINARY_REDACTED,
                "safe"
        );
    }

    @Test
    void doesNotMutateCallerOwnedMapsListsOrBytes() {
        byte[] bytes = {9, 8};
        Map<String, Object> nested = new LinkedHashMap<>(Map.of("passwordHash", "hash"));
        List<Object> list = new ArrayList<>(List.of(bytes, nested));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", list);

        AuditPayloadSanitizer.sanitize(payload);

        assertThat(payload).containsEntry("items", list);
        assertThat(list).containsExactly(bytes, nested);
        assertThat(nested).containsEntry("passwordHash", "hash");
        assertThat(bytes).containsExactly((byte) 9, (byte) 8);
    }
}
