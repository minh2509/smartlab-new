package com.smartlab.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContentCategoryDescriptionMigrationContractTest {

    @Test
    void addsDescriptionAndRepairsCanonicalSeedIdempotently() throws IOException {
        Path migrationPath = Path.of("sql/016_content_category_description_seed.sql");
        assertThat(migrationPath).exists();

        String migration = Files.readString(migrationPath);

        assertThat(migration).contains(
                "BEGIN;",
                "ADD COLUMN IF NOT EXISTS description VARCHAR(500)",
                "INSERT INTO public.content_categories (code, name, description, is_active)",
                "ON CONFLICT (code) DO UPDATE",
                "COALESCE(public.content_categories.description, EXCLUDED.description)",
                "COMMIT;"
        );
        assertThat(migration.toUpperCase()).doesNotContain("DELETE", "TRUNCATE", "DROP ");
    }
}
