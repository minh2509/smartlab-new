package com.smartlab.service;

import java.util.Optional;

/** Internal D2 seam used by post content; it never exposes a storage key or provider URL. */
public interface PostContentFileService {
    Optional<FileMetadata> findActiveMetadata(Long fileId);

    DownloadedContent downloadActiveContent(Long fileId);

    record FileMetadata(Long id, Long ownerUserId, String mimeType, String originalName, boolean image,
                        String accessScope) {
        public FileMetadata(Long id, Long ownerUserId, String mimeType, String originalName, boolean image) {
            this(id, ownerUserId, mimeType, originalName, image, null);
        }
    }

    record DownloadedContent(byte[] content, String mimeType, String originalName) {
    }
}
