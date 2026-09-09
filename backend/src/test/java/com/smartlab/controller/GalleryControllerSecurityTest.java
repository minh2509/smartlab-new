package com.smartlab.controller;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.filter.JwtRequestFilter;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.GalleryService;
import com.smartlab.service.UserSessionService;
import com.smartlab.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GalleryController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, JwtRequestFilter.class})
class GalleryControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean GalleryService galleryService;
    @MockitoBean AppUserDetailService appUserDetailService;
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean UserSessionService userSessionService;

    @Test
    void publicReadIsAnonymousButAdminMutationAreaIsProtected() throws Exception {
        when(galleryService.listPublic(null, null, null, null, com.smartlab.enums.PublicGallerySort.LATEST, 0, 24))
                .thenReturn(new PublicPageResponse<>(List.of(), 0, 24, 0, 0));
        when(galleryService.listAdmin(any(), any(), any(), any(), any(), any(), eq(0), eq(24)))
                .thenReturn(new PublicPageResponse<>(List.of(), 0, 24, 0, 0));
        mockMvc.perform(get("/gallery/public")).andExpect(status().isOk());
        mockMvc.perform(get("/gallery/public/years")).andExpect(status().isOk());
        mockMvc.perform(get("/admin/gallery")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/gallery").with(user("member").roles("MEMBER").authorities(new SimpleGrantedAuthority("GALLERY_MANAGE"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/gallery").with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("GALLERY_MANAGE"))))
                .andExpect(status().isOk());
    }
}
