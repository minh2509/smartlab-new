package com.smartlab.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ChatAttachmentAccessMigrationContractTest {
    @Test
    void restrictsExistingAndFutureChatAttachmentsToConversationMembers() throws IOException {
        String migration = Files.readString(Path.of("sql/021_chat_attachment_access.sql"));

        assertThat(migration).contains(
                "chk_files_access_scope",
                "'CHAT'",
                "SELECT DISTINCT file_id FROM chat_message_files",
                "access_scope = 'CHAT'",
                "shared_file_id",
                "member_profiles",
                "posts",
                "document_versions",
                "gallery_items"
        );
        assertThat(migration.toUpperCase()).doesNotContain("DROP TABLE", "TRUNCATE", "DELETE FROM");
    }
}
