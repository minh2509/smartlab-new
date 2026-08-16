package com.smartlab.service;

import com.smartlab.dto.request.CreateDocumentRequest;
import com.smartlab.dto.request.CreateDocumentVersionRequest;
import com.smartlab.dto.response.DocumentResponse;
import com.smartlab.dto.response.DocumentVersionResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface DocumentService {
    List<DocumentResponse> list(Long projectId, Authentication authentication);

    DocumentResponse create(
            Long projectId,
            CreateDocumentRequest request,
            Authentication authentication
    );

    List<DocumentVersionResponse> listVersions(Long documentId, Authentication authentication);

    DocumentVersionResponse addVersion(
            Long documentId,
            CreateDocumentVersionRequest request,
            Authentication authentication
    );

    FileService.DownloadedFile downloadCurrent(Long documentId, Authentication authentication);

    void delete(Long documentId, Authentication authentication);
}
