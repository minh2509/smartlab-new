package com.smartlab.storage;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class GoogleDriveFileStorageTest {
    @Test
    void restoreUsesDrivePatchToUntrashTheExistingFile() throws Exception {
        GoogleDriveFileStorage storage = new GoogleDriveFileStorage(JsonMapper.builder().build());
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
        org.mockito.Mockito.when(response.statusCode()).thenReturn(200);
        ReflectionTestUtils.setField(storage, "httpClient", httpClient);
        ReflectionTestUtils.setField(storage, "clientId", "test-client");
        ReflectionTestUtils.setField(storage, "clientSecret", "test-secret");
        ReflectionTestUtils.setField(storage, "refreshToken", "test-refresh");
        ReflectionTestUtils.setField(storage, "cachedAccessToken", "test-token");
        ReflectionTestUtils.setField(storage, "accessTokenExpiresAt", Instant.now().plusSeconds(60));

        storage.restore("file-id");

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), any());
        assertThat(request.getValue().method()).isEqualTo("PATCH");
        assertThat(request.getValue().uri().toString())
                .isEqualTo("https://www.googleapis.com/drive/v3/files/file-id?supportsAllDrives=true");
        assertThat(body(request.getValue())).isEqualTo("{\"trashed\":false}");
    }

    private static String body(HttpRequest request) {
        CompletableFuture<String> result = new CompletableFuture<>();
        StringBuilder content = new StringBuilder();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer buffer) {
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                content.append(new String(bytes, StandardCharsets.UTF_8));
            }
            @Override public void onError(Throwable throwable) { result.completeExceptionally(throwable); }
            @Override public void onComplete() { result.complete(content.toString()); }
        });
        return result.join();
    }
}
