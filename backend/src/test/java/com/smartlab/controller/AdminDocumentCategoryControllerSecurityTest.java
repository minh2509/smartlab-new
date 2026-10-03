package com.smartlab.controller;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
import com.smartlab.filter.JwtRequestFilter;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.DocumentCategoryService;
import com.smartlab.service.UserSessionService;
import com.smartlab.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDocumentCategoryController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, JwtRequestFilter.class})
class AdminDocumentCategoryControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private DocumentCategoryService documentCategoryService;
    @MockitoBean private AppUserDetailService appUserDetailService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserSessionService userSessionService;

    @Test
    void categoryAdministrationRequiresDocumentManageOnly() throws Exception {
        when(documentCategoryService.listCategories()).thenReturn(List.of());

        mockMvc.perform(get("/admin/document-categories"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/document-categories")
                        .with(user("project-manager").authorities(() -> "PROJECT_MANAGE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/document-categories")
                        .with(user("role-admin-only").authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(documentCategoryService);

        mockMvc.perform(get("/admin/document-categories")
                        .with(user("document-manager").authorities(() -> "DOCUMENT_MANAGE")))
                .andExpect(status().isOk());
        verify(documentCategoryService).listCategories();
    }
}
