package com.smartlab.service;

import com.smartlab.dto.response.FileResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileService {
    FileResponse upload(MultipartFile file, String accessScope, String description, String email);

    FileResponse uploadForProject(
            MultipartFile file,
            String accessScope,
            String description,
            String email,
            Long projectId
    );

    List<FileResponse> listOwn(String email);

    FileResponse describe(Long id, Authentication authentication);

    boolean canRead(Long id, Authentication authentication);

    DownloadedFile download(Long id, Authentication authentication);

    void delete(Long id, String email, Authentication authentication);

    record DownloadedFile(byte[] content, String mimeType, String originalName) {
    }
}
