package com.smartlab.controller;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAccountController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, JwtRequestFilter.class})
class AdminAccountControllerSecurityTest {
    private static final String UPDATE_BODY = "{\"name\":\"Nguyen Van A\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminAccountService adminAccountService;
    @MockitoBean private BulkAccountInvitationService bulkAccountInvitationService;
    @MockitoBean private AppUserDetailService appUserDetailService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserSessionService userSessionService;

    @Test
    void anonymousCannotReadOrUpdateAccounts() throws Exception {
        mockMvc.perform(get("/admin/accounts")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/admin/accounts/user-id")
                .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(adminAccountService);
    }

    @Test
    void accountInformationRequiresUserManageAuthority() throws Exception {
        mockMvc.perform(get("/admin/accounts").with(roleManager())).andExpect(status().isForbidden());
        mockMvc.perform(patch("/admin/accounts/user-id").with(roleManager())
                .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY)).andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/accounts").with(userManager())).andExpect(status().isOk());
        mockMvc.perform(patch("/admin/accounts/user-id").with(userManager())
                .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY)).andExpect(status().isOk());
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor roleManager() {
        return user("role-manager@smartlab.test").authorities(() -> "ROLE_ADMIN", () -> "ROLE_MANAGE");
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor userManager() {
        return user("user-manager@smartlab.test").authorities(() -> "ROLE_ADMIN", () -> "USER_MANAGE");
    }
}
