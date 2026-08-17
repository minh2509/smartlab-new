package com.smartlab.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleDriveFileStorageIntegrationTest {
    @Test
    @EnabledIfSystemProperty(named = "smartlab.drive.it", matches = "true")
    void uploadsDownloadsAndTrashesTemporaryFile() throws IOException {
        Properties env = loadEnv();
        GoogleDriveFileStorage storage = new GoogleDriveFileStorage(JsonMapper.builder().build());
        setRequiredProperty(storage, "clientId", env, "GOOGLE_DRIVE_CLIENT_ID");
        setRequiredProperty(storage, "clientSecret", env, "GOOGLE_DRIVE_CLIENT_SECRET");
        setRequiredProperty(storage, "refreshToken", env, "GOOGLE_DRIVE_REFRESH_TOKEN");
        setRequiredProperty(storage, "folderId", env, "GOOGLE_DRIVE_FOLDER_ID");

        byte[] expected = "Smart Lab Google Drive smoke test".getBytes(StandardCharsets.UTF_8);
        FileStorage.StoredFile uploaded = storage.upload(
                "smartlab-drive-smoke-test.txt", "text/plain", expected, "Temporary integration test file");
        try {
            assertThat(uploaded.storageKey()).isNotBlank();
            assertThat(storage.download(uploaded.storageKey()).content()).isEqualTo(expected);
        } finally {
            storage.trash(uploaded.storageKey());
        }
    }

    private Properties loadEnv() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(Path.of(".env"))) {
            properties.load(input);
        }
        return properties;
    }

    private void setRequiredProperty(Object target, String field, Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(key + " is required for Drive integration test");
        ReflectionTestUtils.setField(target, field, value.trim());
    }
}
