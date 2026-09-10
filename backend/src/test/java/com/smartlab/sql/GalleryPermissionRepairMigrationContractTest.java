package com.smartlab.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GalleryPermissionRepairMigrationContractTest {

    @Test
    void provisionsGalleryManagePermissionAndAdminAssignmentIdempotently() throws IOException {
        Path migrationPath = Path.of("sql/015_gallery_permission_repair.sql");
        assertThat(migrationPath).exists();

        String migration = Files.readString(migrationPath);

        // Verify idempotent provisioning of GALLERY_MANAGE permission
        assertThat(migration)
                .contains(
                        "INSERT INTO public.permissions (code, name, module, description, is_active, created_at)",
                        "'GALLERY_MANAGE'",
                        "'Quản lý thư viện ảnh'",
                        "'GALLERY'",
                        "ON CONFLICT (code) DO UPDATE SET",
                        "updated_at = now()"
                );

        // Verify idempotent assignment to ADMIN role only
        assertThat(migration)
                .contains(
                        "INSERT INTO public.role_permissions (role_id, permission_id, created_at)",
                        "FROM public.roles r",
                        "CROSS JOIN public.permissions p",
                        "WHERE r.code = 'ADMIN' AND p.code = 'GALLERY_MANAGE'",
                        "ON CONFLICT (role_id, permission_id) DO NOTHING"
                );

        // Verify transaction boundaries
        assertThat(migration)
                .contains("BEGIN;", "COMMIT;");

        // Verify safety constraints: no destructive SQL, no unrelated grants, no fixture modification
        assertThat(migration.toUpperCase())
                .doesNotContain(
                        "DELETE",
                        "TRUNCATE",
                        "DROP TABLE",
                        "DROP ",
                        "'MEMBER'",
                        "'LEADER'",
                        "GALLERY_ITEMS",
                        "FILES",
                        "TBL_USER",
                        "PROJECTS",
                        "ACCOUNT_INVITATIONS"
                );
    }
}
