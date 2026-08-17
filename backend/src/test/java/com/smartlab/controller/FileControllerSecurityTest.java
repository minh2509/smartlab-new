package com.smartlab.controller;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
import com.smartlab.dto.response.FileResponse;
import com.smartlab.filter.JwtRequestFilter;
import com.smartlab.service.AppUserDetailService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, JwtRequestFilter.class})
class FileControllerSecurityTest {
    private static final String EMAIL = "member@smartlab.test";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FileService fileService;
    @MockitoBean private AppUserDetailService appUserDetailService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserSessionService userSessionService;

    @Test
    void currentMemberCanListOnlyTheirOwnFiles() throws Exception {
        when(fileService.listOwn(EMAIL)).thenReturn(List.of(FileResponse.builder().id(22L).build()));

        mockMvc.perform(get("/me/files").with(user(EMAIL)))
                .andExpect(status().isOk());

        verify(fileService).listOwn(EMAIL);
    }

    @Test
    void anonymousCannotListMemberFiles() throws Exception {
        mockMvc.perform(get("/me/files"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(fileService);
    }

    @Test
    void anonymousCannotUploadOrDeleteFiles() throws Exception {
        mockMvc.perform(multipart("/files/upload").file(textFile()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/files/22"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(fileService);
    }

    @Test
    void projectUploadDelegatesProjectContextForAuthorizedMember() throws Exception {
        when(fileService.uploadForProject(any(), eq("PROJECT"), eq("Task input"), eq(EMAIL), eq(7L)))
                .thenReturn(FileResponse.builder().id(22L).accessScope("PROJECT").build());

        mockMvc.perform(multipart("/files/upload")
                        .file(textFile())
                        .param("accessScope", "PROJECT")
                        .param("description", "Task input")
                        .param("projectId", "7")
                        .with(user(EMAIL).authorities(new SimpleGrantedAuthority("FILE_UPLOAD"))))
                .andExpect(status().isOk());

        verify(fileService).uploadForProject(any(), eq("PROJECT"), eq("Task input"), eq(EMAIL), eq(7L));
    }

    @Test
    void authenticatedUserWithoutFilePermissionCannotMutateFiles() throws Exception {
        mockMvc.perform(multipart("/files/upload").file(textFile()).with(user(EMAIL)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/files/22").with(user(EMAIL)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(fileService);
    }

    @Test
    void ownerWithDeletePermissionCanReachOwnershipPolicy() throws Exception {
        mockMvc.perform(delete("/files/22")
                        .with(user(EMAIL).authorities(new SimpleGrantedAuthority("FILE_DELETE"))))
                .andExpect(status().isNoContent());

        verify(fileService).delete(eq(22L), eq(EMAIL), any(Authentication.class));
    }

    @Test
    void anonymousDownloadReachesScopePolicy() throws Exception {
        when(fileService.download(eq(22L), nullable(Authentication.class)))
                .thenReturn(new FileService.DownloadedFile(
                        "public".getBytes(), MediaType.TEXT_PLAIN_VALUE, "public.txt"));

        mockMvc.perform(get("/files/22"))
                .andExpect(status().isOk());

        verify(fileService).download(eq(22L), nullable(Authentication.class));
    }

    private static MockMultipartFile textFile() {
        return new MockMultipartFile(
                "file", "task.txt", MediaType.TEXT_PLAIN_VALUE, "task".getBytes());
    }
}
