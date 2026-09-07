package com.smartlab.sql;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AchievementEditorV2MigrationContractTest {
    @Test
    void addsRecognitionMetadataAndSoftDetachedAchievementFileMappingsWithGrants() throws Exception {
        String sql = Files.readString(Path.of("sql/009_achievement_editor_v2.sql"));
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS lab_achievements", "recognizing_organization VARCHAR(500)", "CREATE TABLE IF NOT EXISTS lab_achievement_files",
                "achievement_id BIGINT NOT NULL REFERENCES lab_achievements(id)", "file_id BIGINT NOT NULL REFERENCES files(id)",
                "label VARCHAR(500)", "sort_order INTEGER NOT NULL DEFAULT 0", "deleted_at TIMESTAMPTZ",
                "CHECK (sort_order >= 0)", "UNIQUE INDEX IF NOT EXISTS uq_lab_achievement_files_active_achievement_file",
                "WHERE deleted_at IS NULL", "idx_lab_achievement_files_active_achievement_sort_id",
                "GRANT SELECT, INSERT, UPDATE, DELETE ON lab_achievement_files TO smartlab_user",
                "GRANT USAGE, SELECT ON SEQUENCE lab_achievement_files_id_seq TO smartlab_user");
    }
}
