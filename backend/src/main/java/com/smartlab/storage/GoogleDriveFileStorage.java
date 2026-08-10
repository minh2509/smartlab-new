package com.smartlab.storage;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class GoogleDriveFileStorage implements FileStorage {
    private static final String DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files";
    private static final String DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final int UPLOAD_CHUNK_SIZE = 8 * 1024 * 1024;
    private static final int MAX_UPLOAD_RECOVERY_ATTEMPTS = 5;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private volatile String cachedAccessToken;
    private volatile Instant accessTokenExpiresAt = Instant.EPOCH;

    @Value("${smartlab.google-drive.client-id:}")
    private String clientId;

    @Value("${smartlab.google-drive.client-secret:}")
    private String clientSecret;

    @Value("${smartlab.google-drive.refresh-token:}")
    private String refreshToken;

    @Value("${smartlab.google-drive.folder-id:}")
    private String folderId;

    @Override
    public StoredFile upload(String originalName, String mimeType, byte[] content, String description) {
        String accessToken = accessToken();
        try {
            tools.jackson.databind.node.ObjectNode metadata = objectMapper.createObjectNode()
                    .put("name", originalName)
                    .put("mimeType", mimeType);
            if (description != null && !description.isBlank()) {
                metadata.put("description", description);
            }
            if (folderId != null && !folderId.isBlank()) {
                metadata.putArray("parents").add(folderId);
            }

            HttpRequest initiationRequest = HttpRequest.newBuilder(URI.create(
                            DRIVE_UPLOAD_URL + "?uploadType=resumable&supportsAllDrives=true"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("X-Upload-Content-Type", mimeType)
                    .header("X-Upload-Content-Length", String.valueOf(content.length))
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(metadata.toString()))
                    .build();
            HttpResponse<String> initiationResponse = httpClient.send(
                    initiationRequest, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(initiationResponse, "initialize file upload");
            String uploadUrl = initiationResponse.headers().firstValue("Location")
                    .orElseThrow(() -> new StorageException("Google Drive did not return an upload URL"));

            HttpResponse<String> response = uploadContent(URI.create(uploadUrl), accessToken, mimeType, content);
            String storageKey = objectMapper.readTree(response.body()).path("id").asText(null);
            if (storageKey == null || storageKey.isBlank()) {
                throw new StorageException("Google Drive did not return a file id");
            }
            return new StoredFile(storageKey, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new StorageException("Unable to upload file to Google Drive", exception);
        } catch (IOException exception) {
            throw new StorageException("Unable to upload file to Google Drive", exception);
        }
    }

    @Override
    public StoredFileContent download(String storageKey) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            DRIVE_FILES_URL + "/" + storageKey + "?alt=media&supportsAllDrives=true"))
                    .timeout(Duration.ofMinutes(2))
                    .header("Authorization", "Bearer " + accessToken())
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            ensureSuccess(response, "download file");
            return new StoredFileContent(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new StorageException("Unable to download file from Google Drive", exception);
        } catch (IOException exception) {
            throw new StorageException("Unable to download file from Google Drive", exception);
        }
    }

    @Override
    public void trash(String storageKey) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            DRIVE_FILES_URL + "/" + storageKey + "?supportsAllDrives=true"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken())
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"trashed\":true}"))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response, "trash file");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new StorageException("Unable to trash file on Google Drive", exception);
        } catch (IOException exception) {
            throw new StorageException("Unable to trash file on Google Drive", exception);
        }
    }

    private HttpResponse<String> uploadContent(
            URI uploadUri,
            String accessToken,
            String mimeType,
            byte[] content
    ) throws IOException, InterruptedException {
        int offset = 0;
        int recoveryAttempts = 0;
        while (offset < content.length) {
            int length = Math.min(UPLOAD_CHUNK_SIZE, content.length - offset);
            int end = offset + length - 1;
            try {
                HttpRequest request = HttpRequest.newBuilder(uploadUri)
                        .timeout(Duration.ofMinutes(2))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", mimeType)
                        .header("Content-Range", "bytes " + offset + "-" + end + "/" + content.length)
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(content, offset, length))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) return response;
                if (response.statusCode() == 308) {
                    int nextOffset = nextUploadOffset(response);
                    recoveryAttempts = nextOffset > offset ? 0 : recoveryAttempts + 1;
                    offset = nextOffset;
                } else if (response.statusCode() >= 500) {
                    recoveryAttempts++;
                    UploadStatus status = queryUploadStatus(uploadUri, accessToken, content.length);
                    if (status.completedResponse() != null) return status.completedResponse();
                    offset = status.nextOffset();
                } else {
                    ensureSuccess(response, "upload file");
                }
            } catch (IOException exception) {
                recoveryAttempts++;
                if (recoveryAttempts > MAX_UPLOAD_RECOVERY_ATTEMPTS) throw exception;
                UploadStatus status = queryUploadStatus(uploadUri, accessToken, content.length);
                if (status.completedResponse() != null) return status.completedResponse();
                offset = status.nextOffset();
            }
            if (recoveryAttempts > MAX_UPLOAD_RECOVERY_ATTEMPTS) {
                throw new StorageException("Google Drive upload could not make progress after retries");
            }
        }
        UploadStatus status = queryUploadStatus(uploadUri, accessToken, content.length);
        if (status.completedResponse() != null) return status.completedResponse();
        throw new StorageException("Google Drive upload ended before all bytes were confirmed");
    }

    private UploadStatus queryUploadStatus(URI uploadUri, String accessToken, int contentLength)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uploadUri)
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Range", "bytes */" + contentLength)
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return new UploadStatus(contentLength, response);
        }
        if (response.statusCode() == 308) {
            return new UploadStatus(nextUploadOffset(response), null);
        }
        ensureSuccess(response, "query upload status");
        throw new StorageException("Unexpected Google Drive upload status");
    }

    private int nextUploadOffset(HttpResponse<?> response) {
        return response.headers().firstValue("Range")
                .map(value -> value.substring(value.lastIndexOf('-') + 1))
                .map(Integer::parseInt)
                .map(lastByte -> lastByte + 1)
                .orElse(0);
    }

    private synchronized String accessToken() {
        if (isBlank(clientId) || isBlank(clientSecret) || isBlank(refreshToken)) {
            throw new StorageException("Google Drive OAuth is not configured");
        }
        if (!isBlank(cachedAccessToken) && Instant.now().isBefore(accessTokenExpiresAt)) {
            return cachedAccessToken;
        }
        String form = "client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&refresh_token=" + encode(refreshToken)
                + "&grant_type=refresh_token";
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response, "refresh Google Drive access token");
            JsonNode tokenResponse = objectMapper.readTree(response.body());
            String token = tokenResponse.path("access_token").asText(null);
            if (token == null || token.isBlank()) {
                throw new StorageException("Google OAuth response did not include an access token");
            }
            long expiresIn = tokenResponse.path("expires_in").asLong(3600);
            cachedAccessToken = token;
            accessTokenExpiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn - 60));
            return cachedAccessToken;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new StorageException("Unable to refresh Google Drive access token", exception);
        } catch (IOException exception) {
            throw new StorageException("Unable to refresh Google Drive access token", exception);
        }
    }

    private void ensureSuccess(HttpResponse<?> response, String operation) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new StorageException("Google Drive failed to " + operation + " (HTTP " + response.statusCode() + ")");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record UploadStatus(int nextOffset, HttpResponse<String> completedResponse) {
    }
}
