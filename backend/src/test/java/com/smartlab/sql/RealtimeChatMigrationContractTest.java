package com.smartlab.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeChatMigrationContractTest {
    @Test
    void preservesTheSingleConversationSequenceAndWatermarkDesign() throws IOException {
        String migration = Files.readString(Path.of("sql/019_realtime_chat_foundation.sql"));

        assertThat(migration).contains(
                "CREATE TABLE IF NOT EXISTS chat_conversations",
                "conversation_type VARCHAR(20) NOT NULL",
                "direct_key VARCHAR(100)",
                "uk_chat_conversations_direct_key",
                "CREATE TABLE IF NOT EXISTS chat_conversation_members",
                "last_read_seq BIGINT NOT NULL DEFAULT 0",
                "uk_chat_conversation_members_conversation_user",
                "CREATE TABLE IF NOT EXISTS chat_messages",
                "uk_chat_messages_conversation_seq",
                "uk_chat_messages_sender_client_id",
                "CREATE TABLE IF NOT EXISTS chat_message_files",
                "uk_chat_message_files_message_file"
        );
        assertThat(migration.toLowerCase())
                .doesNotContain("read_receipt", "seen_messages", "offset");
        assertThat(migration.toUpperCase()).doesNotContain("DROP TABLE", "TRUNCATE", "DELETE FROM");
    }
}
