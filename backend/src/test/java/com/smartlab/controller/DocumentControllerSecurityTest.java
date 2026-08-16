package com.smartlab.controller;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
import com.smartlab.dto.request.CreateDocumentRequest;
import com.smartlab.dto.request.CreateDocumentVersionRequest;
import com.smartlab.filter.JwtRequestFilter;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.DocumentService;
import com.smartlab.service.FileService;
import com.smartlab.service.UserSessionService;
import com.smartlab.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, JwtRequestFilter.class})
class DocumentControllerSecurityTest {
    private static final String EMAIL = "member@smartlab.test";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DocumentService documentService;
    @MockitoBean private AppUserDetailService appUserDetailService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserSessionService userSessionService;

    @Test
    void anonymousCannotReachAnyDocumentEndpoint() throws Exception {
        MockMultipartFile file = textFile();

        mockMvc.perform(get("/projects/7/documents")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/projects/7/documents").file(file).param("title", "Proposal"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/documents/31/versions")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/documents/31/versions").file(file))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/documents/31/download")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/documents/31")).andExpect(status().isUnauthorized());

        verifyNoInteractions(documentService);
    }

    @Test
    void authenticatedAccountReachesServiceForScopeAndProjectAuthorization() throws Exception {
        MockMultipartFile createFile = textFile();
        MockMultipartFile versionFile = textFile();
        when(documentService.list(eq(7L), any(Authentication.class))).thenReturn(List.of());
        when(documentService.listVersions(eq(31L), any(Authentication.class))).thenReturn(List.of());
        when(documentService.downloadCurrent(eq(31L), any(Authentication.class)))
                .thenReturn(new FileService.DownloadedFile(
                        "document".getBytes(),
                        MediaType.TEXT_PLAIN_VALUE,
                        "document.txt"
                ));

        mockMvc.perform(get("/projects/7/documents").with(user(EMAIL)))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/projects/7/documents")
                        .file(createFile)
                        .param("title", "Proposal")
                        .param("accessScope", "PROJECT")
                        .with(user(EMAIL)))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/documents/31/versions").with(user(EMAIL)))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/documents/31/versions")
                        .file(versionFile)
                        .param("accessScope", "PROJECT")
                        .with(user(EMAIL)))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/documents/31/download").with(user(EMAIL)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/documents/31").with(user(EMAIL)))
                .andExpect(status().isNoContent());

        verify(documentService).list(eq(7L), any(Authentication.class));
        verify(documentService).create(eq(7L), any(CreateDocumentRequest.class), any(Authentication.class));
        verify(documentService).listVersions(eq(31L), any(Authentication.class));
        verify(documentService).addVersion(
                eq(31L),
                any(CreateDocumentVersionRequest.class),
                any(Authentication.class)
        );
        verify(documentService).downloadCurrent(eq(31L), any(Authentication.class));
        verify(documentService).delete(eq(31L), any(Authentication.class));
    }

    private static MockMultipartFile textFile() {
        return new MockMultipartFile(
                "file",
                "document.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "document".getBytes()
        );
    }
}
