package com.smartlab.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectJoinRequestSchemaContractTest {

    @Test
    void canonicalAndIncrementalSchemasMatchRuntimeJoinRequestContract() throws IOException {
        String canonical = Files.readString(
                Path.of("sql/001_smartlab_postgres_schema_seed.sql")
        );
        String migration = Files.readString(
                Path.of("sql/004_project_join_requests.sql")
        );

        assertJoinRequestSchema(canonical);
        assertJoinRequestSchema(migration);

        assertThat(canonical)
                .contains("project_join_requests")
                .contains("project_join_requests_id_seq");

        assertThat(migration)
                .contains("GRANT SELECT, INSERT, UPDATE, DELETE")
                .contains("ON project_join_requests")
                .contains("ON SEQUENCE project_join_requests_id_seq");
    }

    private static void assertJoinRequestSchema(String sql) {
        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS project_join_requests")
                .contains("project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE")
                .contains("requester_user_id BIGINT NOT NULL REFERENCES tbl_user(id) ON DELETE CASCADE")
                .contains("reviewed_by_user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL")
                .contains("status VARCHAR(20) NOT NULL DEFAULT 'PENDING'")
                .contains("status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')")
                .contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_project_join_requests_pending_project_requester")
                .contains("ON project_join_requests(project_id, requester_user_id)")
                .contains("WHERE status = 'PENDING'");
    }
}
