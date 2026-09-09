package com.smartlab.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateCopyRefreshMigrationContractTest {
    @Test
    void refreshesOnlyKnownDefaultTemplatesWithoutDestructiveDataChanges() throws IOException {
        String migration = Files.readString(Path.of("sql/014_email_template_copy_refresh.sql"));

        assertThat(migration)
                .contains(
                        "after 013_bulk_account_invitations.sql",
                        "UPDATE email_templates",
                        "WHERE code = 'ACCOUNT_INVITATION'",
                        "WHERE code = 'PASSWORD_RESET_OTP'",
                        "AND subject_template =",
                        "AND body_template =")
                .doesNotContain(
                        "DROP TABLE",
                        "TRUNCATE",
                        "DELETE FROM",
                        "UPDATE tbl_user",
                        "UPDATE account_invitations",
                        "INSERT INTO");
    }
}
