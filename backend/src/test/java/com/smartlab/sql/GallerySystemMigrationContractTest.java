package com.smartlab.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GallerySystemMigrationContractTest {
    @Test
    void createsOnlyTheAdditiveGallerySchemaAndAdminPermissionContract() throws IOException {
        String migration = Files.readString(Path.of("sql/012_gallery_system.sql"));

        assertThat(migration)
                .contains(
                        "CREATE TABLE IF NOT EXISTS public.gallery_items",
                        "file_id BIGINT NOT NULL REFERENCES public.files(id) ON DELETE RESTRICT",
                        "project_id BIGINT REFERENCES public.projects(id) ON DELETE SET NULL",
                        "event_id BIGINT REFERENCES public.events(id) ON DELETE SET NULL",
                        "created_by_user_id BIGINT REFERENCES public.tbl_user(id) ON DELETE SET NULL",
                        "category IN ('WORKSHOP', 'PROJECT_DEMO', 'EVENT', 'LAB_ACTIVITY', 'OTHER')",
                        "status IN ('DRAFT', 'PUBLISHED')",
                        "uq_gallery_items_active_file",
                        "idx_gallery_items_active_status_updated_id",
                        "idx_gallery_items_public_capture_id",
                        "idx_gallery_items_active_project",
                        "VALUES ('GALLERY_MANAGE'",
                        "WHERE r.code = 'ADMIN' AND p.code = 'GALLERY_MANAGE'",
                        "ON CONFLICT (role_id, permission_id) DO NOTHING")
                .doesNotContain(
                        "INSERT INTO public.gallery_items",
                        "INSERT INTO gallery_items",
                        "DROP TABLE",
                        "TRUNCATE",
                        "DELETE FROM",
                        "research_publications",
                        "010_");
    }
}
