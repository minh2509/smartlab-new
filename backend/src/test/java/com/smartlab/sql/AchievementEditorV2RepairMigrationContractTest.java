package com.smartlab.sql;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AchievementEditorV2RepairMigrationContractTest {
    @Test
    void containsOnlyTheAdditiveAchievementEditorRepairContract() throws Exception {
        String sql = Files.readString(Path.of("sql/011_achievement_editor_v2_repair.sql"));

        assertThat(sql)
                .contains(
                        "ALTER TABLE public.lab_achievements",
                        "ADD COLUMN IF NOT EXISTS recognizing_organization VARCHAR(500)",
                        "CREATE TABLE IF NOT EXISTS public.lab_achievement_files",
                        "achievement_id BIGINT NOT NULL REFERENCES public.lab_achievements(id)",
                        "file_id BIGINT NOT NULL REFERENCES public.files(id)",
                        "label VARCHAR(500)",
                        "sort_order INTEGER NOT NULL DEFAULT 0",
                        "CONSTRAINT chk_lab_achievement_files_sort_order CHECK (sort_order >= 0)",
                        "uq_lab_achievement_files_active_achievement_file",
                        "idx_lab_achievement_files_active_achievement_sort_id",
                        "GRANT SELECT, INSERT, UPDATE, DELETE ON public.lab_achievement_files TO smartlab_user",
                        "GRANT USAGE, SELECT ON SEQUENCE public.lab_achievement_files_id_seq TO smartlab_user")
                .doesNotContain(
                        "research_publications",
                        "010_",
                        "INSERT INTO lab_achievements",
                        "UPDATE lab_achievements",
                        "DELETE FROM lab_achievements",
                        "TRUNCATE",
                        "DROP TABLE lab_achievements",
                        "DROP COLUMN",
                        "setval");
    }
}
