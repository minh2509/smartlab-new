package com.smartlab.controller;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
import com.smartlab.filter.JwtRequestFilter;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.MemberProfileService;
import com.smartlab.service.ResearchFieldService;
import com.smartlab.service.UserSessionService;
import com.smartlab.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({MemberProfileController.class, ResearchFieldController.class})
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, JwtRequestFilter.class})
class MemberProfileResearchFieldSecurityTest {
    private static final String EMAIL = "member@smartlab.test";

    @Autowired MockMvc mockMvc;
    @MockitoBean MemberProfileService memberProfileService;
    @MockitoBean ResearchFieldService researchFieldService;
    @MockitoBean AppUserDetailService appUserDetailService;
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean UserSessionService userSessionService;

    @Test
    void anonymousCannotReadMemberCollectionButCanReadPublicCollections() throws Exception {
        when(researchFieldService.listActive()).thenReturn(List.of());

        mockMvc.perform(get("/members")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/research-fields")).andExpect(status().isOk());
        mockMvc.perform(get("/me/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/members")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/research-fields")).andExpect(status().isUnauthorized());

        verifyNoInteractions(memberProfileService);
        verify(researchFieldService).listActive();
    }

    @Test
    void authenticatedUserWithoutD2PermissionsIsForbidden() throws Exception {
        mockMvc.perform(get("/me/profile").with(user(EMAIL)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/me/profile")
                        .with(user(EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/members").with(user(EMAIL)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/research-fields").with(user(EMAIL)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(memberProfileService, researchFieldService);
    }

    @Test
    void matchingPermissionsReachProfileAndAdminServices() throws Exception {
        when(memberProfileService.getOwnProfile(EMAIL)).thenReturn(null);
        when(memberProfileService.updateOwnProfile(eq(EMAIL), any())).thenReturn(null);
        when(memberProfileService.listAllMembers()).thenReturn(List.of());
        when(researchFieldService.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/me/profile").with(user(EMAIL)
                        .authorities(new SimpleGrantedAuthority("PROFILE_READ"))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/me/profile")
                        .with(user(EMAIL).authorities(new SimpleGrantedAuthority("PROFILE_UPDATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/members").with(user(EMAIL)
                        .authorities(new SimpleGrantedAuthority("MEMBER_MANAGE"))))
                .andExpect(status().isOk());
        when(memberProfileService.listMembers(null, null, null)).thenReturn(List.of());
        mockMvc.perform(get("/members").with(user(EMAIL))).andExpect(status().isOk());
        mockMvc.perform(get("/admin/research-fields").with(user(EMAIL)
                        .authorities(new SimpleGrantedAuthority("RESEARCH_FIELD_MANAGE"))))
                .andExpect(status().isOk());

        verify(memberProfileService).getOwnProfile(EMAIL);
        verify(memberProfileService).updateOwnProfile(eq(EMAIL), any());
        verify(memberProfileService).listAllMembers();
        verify(memberProfileService).listMembers(null, null, null);
        verify(researchFieldService).listAll();
    }
}
