package com.smartlab.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LabArticleMigrationContractTest {
    @Test void publishedArticlesRequirePublishedAtWhileDraftsMayPreserveHistoricalTimestamps() throws IOException {
        String migration = Files.readString(Path.of("sql/007_lab_articles.sql"));

        assertThat(migration).contains("CONSTRAINT chk_lab_articles_published_at", "status <> 'PUBLISHED' OR published_at IS NOT NULL")
                .doesNotContain("status = 'DRAFT' AND published_at IS NULL");
    }
}
