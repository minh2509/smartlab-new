package com.smartlab.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchFieldCoverMigrationContractTest {
    @Test
    void addsNullableResearchFieldCoverReferenceWithSetNullLifecycle() throws IOException {
        String migration = Files.readString(Path.of("sql/008_research_field_cover_image.sql"));

        assertThat(migration).contains("ALTER TABLE research_fields", "cover_file_id BIGINT NULL",
                "REFERENCES files(id) ON DELETE SET NULL");
    }
}
