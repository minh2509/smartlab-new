package com.smartlab.sql;

import com.smartlab.entity.ChatMessageEntity;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRetryFingerprintMigrationContractTest {
    @Test
    void addsNullableOriginalPayloadFingerprintWithoutRewritingHistory() throws IOException {
        String migration = Files.readString(Path.of("sql/023_chat_message_retry_fingerprint.sql"));
        assertThat(migration).contains("ADD COLUMN IF NOT EXISTS original_payload_fingerprint VARCHAR(64)");
        assertThat(migration.toUpperCase()).doesNotContain("NOT NULL", "DELETE FROM", "TRUNCATE", "DROP TABLE");
    }

    @Test
    void entityMappingAgreesWithNullableVarchar64Migration() throws NoSuchFieldException {
        Column column = ChatMessageEntity.class.getDeclaredField("originalPayloadFingerprint")
                .getAnnotation(Column.class);
        assertThat(column.name()).isEqualTo("original_payload_fingerprint");
        assertThat(column.length()).isEqualTo(64);
        assertThat(column.nullable()).isTrue();
    }
}
