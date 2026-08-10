package com.smartlab.controller;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
import com.smartlab.filter.JwtRequestFilter;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.NotificationService;
import com.smartlab.service.UserSessionService;
import com.smartlab.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, JwtRequestFilter.class, CustomAuthenticationEntryPoint.class})
class NotificationControllerTest {
    private static final String EMAIL = "notification-member@example.edu";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private AppUserDetailService appUserDetailService;
    @MockitoBean private UserSessionService userSessionService;
    @MockitoBean private JwtUtil jwtUtil;

    @Test
    void getRequiresReadOwnAndAllowsIt() throws Exception {
        mockMvc.perform(get("/me/notifications")).andExpect(status().isUnauthorized());
        verifyNoInteractions(notificationService);

        mockMvc.perform(get("/me/notifications").with(authenticated()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(notificationService);

        when(notificationService.getNotifications(EMAIL)).thenReturn(List.of());
        mockMvc.perform(get("/me/notifications").with(authenticated("notifications.read_own")))
                .andExpect(status().isOk());
        verify(notificationService).getNotifications(EMAIL);
    }

    @Test
    void markOneRequiresMarkReadOwnAndAllowsIt() throws Exception {
        mockMvc.perform(patch("/me/notifications/{id}/read", 17L)).andExpect(status().isUnauthorized());
        verifyNoInteractions(notificationService);

        mockMvc.perform(patch("/me/notifications/{id}/read", 17L).with(authenticated()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(notificationService);

        mockMvc.perform(patch("/me/notifications/{id}/read", 17L).with(authenticated("notifications.mark_read_own")))
                .andExpect(status().isNoContent());
        verify(notificationService).markRead(EMAIL, 17L);
    }

    @Test
    void markAllRequiresMarkReadOwnAndAllowsIt() throws Exception {
        mockMvc.perform(patch("/me/notifications/read-all")).andExpect(status().isUnauthorized());
        verifyNoInteractions(notificationService);

        mockMvc.perform(patch("/me/notifications/read-all").with(authenticated()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(notificationService);

        mockMvc.perform(patch("/me/notifications/read-all").with(authenticated("notifications.mark_read_own")))
                .andExpect(status().isNoContent());
        verify(notificationService).markAllRead(EMAIL);
    }

    @Test
    void deleteRequiresOnlyGlobalAuthenticationAndNoNotificationAuthority() throws Exception {
        mockMvc.perform(delete("/me/notifications/{id}", 17L)).andExpect(status().isUnauthorized());
        verifyNoInteractions(notificationService);

        mockMvc.perform(delete("/me/notifications/{id}", 17L).with(authenticated()))
                .andExpect(status().isNoContent());
        verify(notificationService).softDelete(EMAIL, 17L);
        verify(notificationService, never()).getNotifications(any());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor authenticated(String... authorities) {
        return request -> {
            org.springframework.security.core.context.SecurityContext context =
                    org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    EMAIL,
                    "test-only",
                    java.util.Arrays.stream(authorities)
                            .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                            .toList()
            ));
            request.getSession(true).setAttribute(
                    org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    context
            );
            request.setAttribute(
                    org.springframework.security.web.context.RequestAttributeSecurityContextRepository.DEFAULT_REQUEST_ATTR_NAME,
                    context
            );
            return request;
        };
    }
}
