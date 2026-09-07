package com.smartlab.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LandingPageMigrationCompatibilityContractTest {
    @Test
    void migration005PreservesTheHistoricalResearchPublicationContract() throws IOException {
        String migration = Files.readString(Path.of("sql/005_landing_page_data_contracts.sql"));

        assertThat(migration).contains("CREATE TABLE IF NOT EXISTS research_publications",
                        "authors TEXT NOT NULL", "publication_type VARCHAR(30) NOT NULL", "venue VARCHAR(500) NOT NULL",
                        "publication_year INTEGER NOT NULL", "public_url VARCHAR(2048)",
                        "idx_research_publications_public_year_date_id")
                .doesNotContain("CREATE TABLE IF NOT EXISTS lab_achievements");
    }

    @Test
    void newAchievementContractIsBootstrappedBeforeLegacyDataTransition() throws IOException {
        String migration009 = Files.readString(Path.of("sql/009_achievement_editor_v2.sql"));
        String migration010 = Files.readString(Path.of("sql/010_research_publications_to_lab_achievements.sql"));

        assertThat(migration009).contains("CREATE TABLE IF NOT EXISTS lab_achievements",
                "recognizing_organization VARCHAR(500)", "CREATE TABLE IF NOT EXISTS lab_achievement_files",
                "REFERENCES lab_achievements(id)", "REFERENCES files(id)");
        assertThat(migration010).contains("requires historical research_publications",
                "FROM research_publications", "publication.publication_type", "publication.public_url",
                "conflicting lab_achievements IDs", "ON CONFLICT (id) DO NOTHING",
                "COMMENT ON TABLE research_publications");
    }

    @Test
    void canonicalNumericOrderKeepsDependenciesBeforeTransition() {
        assertThat(Path.of("sql/005_landing_page_data_contracts.sql").getFileName().toString()).startsWith("005_");
        assertThat(Path.of("sql/006_lab_news_articles.sql").getFileName().toString()).startsWith("006_");
        assertThat(Path.of("sql/007_lab_articles.sql").getFileName().toString()).startsWith("007_");
        assertThat(Path.of("sql/008_research_field_cover_image.sql").getFileName().toString()).startsWith("008_");
        assertThat(Path.of("sql/009_achievement_editor_v2.sql").getFileName().toString()).startsWith("009_");
        assertThat(Path.of("sql/010_research_publications_to_lab_achievements.sql").getFileName().toString()).startsWith("010_");
    }
}
