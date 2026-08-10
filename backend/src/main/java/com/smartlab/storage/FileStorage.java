package com.smartlab.storage;

public interface FileStorage {
    StoredFile upload(String originalName, String mimeType, byte[] content, String description);

    StoredFileContent download(String storageKey);

    void trash(String storageKey);

    record StoredFile(String storageKey, String publicUrl) {
    }

    record StoredFileContent(byte[] content) {
    }
}
