package com.smartlab.controller;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
import com.smartlab.dto.response.BulkAccountInvitationPreviewResponse;
import com.smartlab.filter.JwtRequestFilter;
import com.smartlab.service.AdminAccountService;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.BulkAccountInvitationService;
import com.smartlab.service.UserSessionService;
import com.smartlab.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAccountController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, JwtRequestFilter.class})
class AdminAccountControllerSecurityTest {
    private static final String PREVIEW_BODY = """
            {"items":[{"fullName":"QA User","email":"qa@example.test"}],"roleCodes":["MEMBER"]}
            """;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminAccountService adminAccountService;
    @MockitoBean private BulkAccountInvitationService bulkAccountInvitationService;
    @MockitoBean private AppUserDetailService appUserDetailService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserSessionService userSessionService;

    @Test
    void bulkInvitationPreviewRequiresUserManagePermission() throws Exception {
        mockMvc.perform(previewRequest())
                .andExpect(status().isUnauthorized());
        mockMvc.perform(previewRequest().with(user("member@example.test").roles("MEMBER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(previewRequest().with(user("leader@example.test").roles("LEADER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(bulkAccountInvitationService);

        when(bulkAccountInvitationService.preview(any()))
                .thenReturn(BulkAccountInvitationPreviewResponse.builder()
                        .requestedCount(1)
                        .acceptedCount(1)
                        .rejectedCount(0)
                        .items(List.of())
                        .build());
        mockMvc.perform(previewRequest().with(user("admin@example.test")
                        .roles("ADMIN")
                        .authorities(() -> "USER_MANAGE")))
                .andExpect(status().isOk());

        verify(bulkAccountInvitationService).preview(any());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder previewRequest() {
        return post("/admin/accounts/invitation-batches/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(PREVIEW_BODY);
    }
}
