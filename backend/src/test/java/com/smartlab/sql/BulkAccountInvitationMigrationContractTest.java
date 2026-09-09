package com.smartlab.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BulkAccountInvitationMigrationContractTest {
    @Test
    void createsTheAdditiveBulkInvitationAndEmailOutboxContract() throws IOException {
        String migration = Files.readString(Path.of("sql/013_bulk_account_invitations.sql"));

        assertThat(migration)
                .contains(
                        "CREATE TABLE IF NOT EXISTS account_invitation_batches",
                        "CREATE TABLE IF NOT EXISTS account_invitation_batch_roles",
                        "CREATE TABLE IF NOT EXISTS account_invitation_items",
                        "CREATE TABLE IF NOT EXISTS email_templates",
                        "CREATE TABLE IF NOT EXISTS email_outbox",
                        "REFERENCES roles(id) ON DELETE RESTRICT",
                        "REFERENCES account_invitations(id) ON DELETE SET NULL",
                        "idx_account_invitation_items_batch_status",
                        "idx_account_invitation_items_email",
                        "idx_email_outbox_available",
                        "idx_email_outbox_batch_item",
                        "uq_email_outbox_active_invitation",
                        "status IN ('QUEUED', 'PROCESSING')",
                        "ON CONFLICT (code) DO NOTHING")
                .doesNotContain(
                        "DROP TABLE",
                        "TRUNCATE",
                        "DELETE FROM",
                        "UPDATE tbl_user",
                        "UPDATE account_invitations");
    }
}
