package com.smartlab.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentCategoryMigrationContractTest {

    @Test
    void provisionsDocumentCategoriesIdempotently() throws IOException {
        Path migrationPath = Path.of("sql/018_document_categories.sql");
        assertThat(migrationPath).exists();

        String migration = Files.readString(migrationPath);

        assertThat(migration).contains(
                "BEGIN;",
                "CREATE TABLE IF NOT EXISTS public.document_categories",
                "ALTER TABLE public.documents",
                "ADD COLUMN IF NOT EXISTS category_id BIGINT",
                "ADD COLUMN IF NOT EXISTS archive_date TIMESTAMPTZ",
                "SET archive_date = created_at",
                "fk_documents_category",
                "idx_documents_archive_date_active",
                "idx_documents_category_archive_active",
                "idx_document_categories_active_order",
                "INSERT INTO public.document_categories",
                "ON CONFLICT (code) DO UPDATE",
                "INSERT INTO public.permissions",
                "COMMIT;"
        );
        assertThat(migration).contains(
                "code = EXCLUDED.code",
                "ALTER COLUMN archive_date SET NOT NULL",
                "ON DELETE SET NULL"
        );
        assertThat(migration).doesNotContain(
                "name = EXCLUDED.name",
                "display_order = EXCLUDED.display_order",
                "is_active = EXCLUDED.is_active"
        );
        assertThat(migration.toUpperCase()).doesNotContain("DELETE FROM", "TRUNCATE", "DROP ");
    }
}
