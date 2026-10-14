package com.smartlab.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessengerFeaturesMigrationContractTest {
    @Test
    void addsDurableMessengerFeaturesWithoutDestructiveStatements() throws IOException {
        String migration = Files.readString(Path.of("sql/020_chat_messenger_features.sql"));

        assertThat(migration).contains(
                "forwarded_from_message_id",
                "metadata_json",
                "'VOICE'",
                "'POLL'",
                "CREATE TABLE IF NOT EXISTS chat_message_reactions",
                "uk_chat_message_reactions_message_user_emoji",
                "idx_chat_messages_content_search"
        );
        assertThat(migration.toUpperCase()).doesNotContain("DROP TABLE", "TRUNCATE", "DELETE FROM");
    }
}
