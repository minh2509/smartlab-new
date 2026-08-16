package com.smartlab.controller;

import com.smartlab.dto.request.CreateDocumentRequest;
import com.smartlab.dto.request.CreateDocumentVersionRequest;
import com.smartlab.dto.response.DocumentResponse;
import com.smartlab.dto.response.DocumentVersionResponse;
import com.smartlab.dto.response.FileResponse;
import com.smartlab.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {
    private static final UsernamePasswordAuthenticationToken AUTHENTICATION =
            new UsernamePasswordAuthenticationToken("leader@smartlab.test", null, List.of());

    @Mock private DocumentService documentService;
    @InjectMocks private DocumentController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void createsDocumentFromFlatMultipartFields() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proposal.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "proposal".getBytes()
        );
        when(documentService.create(eq(7L), any(CreateDocumentRequest.class), eq(AUTHENTICATION)))
                .thenReturn(documentResponse());

        mockMvc.perform(multipart("/projects/7/documents")
                        .file(file)
                        .param("title", "Proposal")
                        .param("description", "Initial")
                        .param("accessScope", "PROJECT")
                        .param("note", "Version one")
                        .principal(AUTHENTICATION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(31))
                .andExpect(jsonPath("$.projectId").value(7))
                .andExpect(jsonPath("$.currentFile.id").value(101))
                .andExpect(jsonPath("$.currentVersionNo").value(1));

        ArgumentCaptor<CreateDocumentRequest> captor = ArgumentCaptor.forClass(CreateDocumentRequest.class);
        verify(documentService).create(eq(7L), captor.capture(), eq(AUTHENTICATION));
        assertThat(captor.getValue().getFile().getOriginalFilename()).isEqualTo("proposal.txt");
        assertThat(captor.getValue().getAccessScope()).isEqualTo("PROJECT");
    }

    @Test
    void rejectsBlankDocumentTitleBeforeService() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proposal.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "proposal".getBytes()
        );

        mockMvc.perform(multipart("/projects/7/documents")
                        .file(file)
                        .param("title", "  ")
                        .principal(AUTHENTICATION))
                .andExpect(status().isBadRequest());

        verify(documentService, never()).create(any(), any(), any());
    }

    @Test
    void uploadsNextVersionAndReturnsVersionContract() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proposal-v2.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "revised".getBytes()
        );
        when(documentService.addVersion(eq(31L), any(CreateDocumentVersionRequest.class), eq(AUTHENTICATION)))
                .thenReturn(DocumentVersionResponse.builder()
                        .id(42L)
                        .documentId(31L)
                        .versionNo(2)
                        .file(FileResponse.builder().id(102L).accessScope("LAB").build())
                        .note("Revised")
                        .build());

        mockMvc.perform(multipart("/documents/31/versions")
                        .file(file)
                        .param("accessScope", "LAB")
                        .param("note", "Revised")
                        .principal(AUTHENTICATION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(31))
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.file.accessScope").value("LAB"));
    }

    @Test
    void softDeletesDocumentWithNoResponseBody() throws Exception {
        mockMvc.perform(delete("/documents/31").principal(AUTHENTICATION))
                .andExpect(status().isNoContent());

        verify(documentService).delete(31L, AUTHENTICATION);
    }

    @Test
    void listsDocumentsAtProjectScopedEndpoint() throws Exception {
        when(documentService.list(7L, AUTHENTICATION)).thenReturn(List.of(documentResponse()));

        mockMvc.perform(get("/projects/7/documents").principal(AUTHENTICATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Proposal"));
    }

    private static DocumentResponse documentResponse() {
        return DocumentResponse.builder()
                .id(31L)
                .projectId(7L)
                .title("Proposal")
                .description("Initial")
                .currentFile(FileResponse.builder()
                        .id(101L)
                        .originalName("proposal.txt")
                        .mimeType(MediaType.TEXT_PLAIN_VALUE)
                        .sizeBytes(8L)
                        .accessScope("PROJECT")
                        .build())
                .currentVersionNo(1)
                .build();
    }
}
