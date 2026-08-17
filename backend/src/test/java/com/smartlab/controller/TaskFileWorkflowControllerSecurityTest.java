package com.smartlab.controller;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
import com.smartlab.dto.response.TaskAttachmentResponse;
import com.smartlab.dto.response.TaskDetailResponse;
import com.smartlab.enums.AttachmentType;
import com.smartlab.filter.JwtRequestFilter;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.TaskFileWorkflowService;
import com.smartlab.service.TaskService;
import com.smartlab.service.UserSessionService;
import com.smartlab.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, JwtRequestFilter.class})
class TaskFileWorkflowControllerSecurityTest {
    private static final String EMAIL = "member@smartlab.test";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TaskService taskService;
    @MockitoBean private TaskFileWorkflowService taskFileWorkflowService;
    @MockitoBean private AppUserDetailService appUserDetailService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserSessionService userSessionService;

    @Test
    void combinedAttachmentUploadRequiresFileUpload() throws Exception {
        mockMvc.perform(multipart("/tasks/11/attachments/upload")
                        .file(textFile())
                        .param("attachmentType", "REFERENCE")
                        .with(user(EMAIL)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(taskFileWorkflowService);
    }

    @Test
    void fileUploadAuthorityWithoutFileDeleteCanDelegateAttachmentWorkflow() throws Exception {
        when(taskFileWorkflowService.uploadAndAddAttachment(
                any(), any(), any(), any(), any())).thenReturn(TaskAttachmentResponse.builder().id(1L).build());

        mockMvc.perform(multipart("/tasks/11/attachments/upload")
                        .file(textFile())
                        .param("attachmentType", "REFERENCE")
                        .param("description", "Task attachment")
                        .with(user(EMAIL).authorities(new SimpleGrantedAuthority("FILE_UPLOAD"))))
                .andExpect(status().isCreated());

        verify(taskFileWorkflowService).uploadAndAddAttachment(
                eq(11L), any(), eq(AttachmentType.REFERENCE), eq("Task attachment"), eq(EMAIL));
    }

    @Test
    void fileUploadAuthorityWithoutFileDeleteCanDelegateSubmissionWorkflow() throws Exception {
        when(taskFileWorkflowService.uploadAndSubmit(any(), any(), any(), any()))
                .thenReturn(TaskDetailResponse.builder().id(11L).build());

        mockMvc.perform(multipart("/tasks/11/submit/upload")
                        .file(textFile())
                        .param("note", "Result note")
                        .with(user(EMAIL).authorities(new SimpleGrantedAuthority("FILE_UPLOAD"))))
                .andExpect(status().isOk());

        verify(taskFileWorkflowService).uploadAndSubmit(eq(11L), any(), eq("Result note"), eq(EMAIL));
    }

    private static MockMultipartFile textFile() {
        return new MockMultipartFile("file", "task.txt", MediaType.TEXT_PLAIN_VALUE, "task".getBytes());
    }
}
