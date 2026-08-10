package com.smartlab.service;

import com.smartlab.dto.response.FileResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {
    FileResponse upload(MultipartFile file, String accessScope, String description, String email);

    DownloadedFile download(Long id, Authentication authentication);

    void delete(Long id, String email, Authentication authentication);

    record DownloadedFile(byte[] content, String mimeType, String originalName) {
    }
}
