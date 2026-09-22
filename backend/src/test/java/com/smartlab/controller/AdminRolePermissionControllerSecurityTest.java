package com.smartlab.controller;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
import com.smartlab.filter.JwtRequestFilter;
import com.smartlab.service.AdminRolePermissionService;
import com.smartlab.service.AppUserDetailService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminRolePermissionController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, JwtRequestFilter.class})
class AdminRolePermissionControllerSecurityTest {
    private static final String ROLE_BODY = "{\"code\":\"RESEARCH_ASSISTANT\",\"name\":\"Research Assistant\",\"isActive\":true}";
    private static final String PERMISSION_BODY = "{\"code\":\"PROJECT_EXPORT\",\"name\":\"Export projects\",\"module\":\"PROJECT\",\"isActive\":true}";
    private static final String ROLE_PERMISSIONS_BODY = "{\"permissionCodes\":[\"PROJECT_EXPORT\"]}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminRolePermissionService adminRolePermissionService;
    @MockitoBean private AppUserDetailService appUserDetailService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserSessionService userSessionService;

    @Test
    void anonymousCannotAccessRbacAdministration() throws Exception {
        mockMvc.perform(get("/admin/roles")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/permissions")).andExpect(status().isUnauthorized());
        verifyNoInteractions(adminRolePermissionService);
    }

    @Test
    void roleManagementRequiresRoleManageAuthority() throws Exception {
        mockMvc.perform(get("/admin/roles").with(permissionManager())).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/roles").with(permissionManager())
                .contentType(MediaType.APPLICATION_JSON).content(ROLE_BODY)).andExpect(status().isForbidden());
        mockMvc.perform(put("/admin/roles/RESEARCH_ASSISTANT").with(permissionManager())
                .contentType(MediaType.APPLICATION_JSON).content(ROLE_BODY)).andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/roles").with(roleManager())).andExpect(status().isOk());
        mockMvc.perform(post("/admin/roles").with(roleManager())
                .contentType(MediaType.APPLICATION_JSON).content(ROLE_BODY)).andExpect(status().isOk());
        mockMvc.perform(put("/admin/roles/RESEARCH_ASSISTANT").with(roleManager())
                .contentType(MediaType.APPLICATION_JSON).content(ROLE_BODY)).andExpect(status().isOk());
    }

    @Test
    void permissionManagementRequiresPermissionManageAuthority() throws Exception {
        mockMvc.perform(get("/admin/permissions").with(roleManager())).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/permissions").with(roleManager())
                .contentType(MediaType.APPLICATION_JSON).content(PERMISSION_BODY)).andExpect(status().isForbidden());
        mockMvc.perform(put("/admin/permissions/PROJECT_EXPORT").with(roleManager())
                .contentType(MediaType.APPLICATION_JSON).content(PERMISSION_BODY)).andExpect(status().isForbidden());
        mockMvc.perform(put("/admin/roles/LEADER/permissions").with(roleManager())
                .contentType(MediaType.APPLICATION_JSON).content(ROLE_PERMISSIONS_BODY)).andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/permissions").with(permissionManager())).andExpect(status().isOk());
        mockMvc.perform(post("/admin/permissions").with(permissionManager())
                .contentType(MediaType.APPLICATION_JSON).content(PERMISSION_BODY)).andExpect(status().isOk());
        mockMvc.perform(put("/admin/permissions/PROJECT_EXPORT").with(permissionManager())
                .contentType(MediaType.APPLICATION_JSON).content(PERMISSION_BODY)).andExpect(status().isOk());
        mockMvc.perform(put("/admin/roles/LEADER/permissions").with(permissionManager())
                .contentType(MediaType.APPLICATION_JSON).content(ROLE_PERMISSIONS_BODY)).andExpect(status().isOk());
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor roleManager() {
        return user("role-manager@smartlab.test").authorities(() -> "ROLE_ADMIN", () -> "ROLE_MANAGE");
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor permissionManager() {
        return user("permission-manager@smartlab.test").authorities(() -> "ROLE_ADMIN", () -> "PERMISSION_MANAGE");
    }
}
