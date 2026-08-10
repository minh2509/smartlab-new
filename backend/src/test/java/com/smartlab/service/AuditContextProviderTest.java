package com.smartlab.service;

import com.smartlab.entity.UserEntity;
import com.smartlab.repo.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditContextProviderTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AuditContextProvider provider = new AuditContextProvider(users);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }

    @Test void resolvesCanonicalActorFirstForwardedIpAndUserAgent() {
        authenticate("actor@example.edu");
        when(users.findByEmail("actor@example.edu")).thenReturn(Optional.of(UserEntity.builder().id(12L).build()));
        MockHttpServletRequest request = request("203.0.113.8, 10.0.0.2", "127.0.0.1", "browser");
        AuditContextProvider.AuditContext context = provider.current();
        assertThat(context).isEqualTo(new AuditContextProvider.AuditContext(12L, "203.0.113.8", "browser"));
    }

    @Test void fallsBackToRemoteAddressWithoutTruncation() {
        request("x".repeat(46), "192.0.2.1", null);
        assertThat(provider.current().ipAddress()).isEqualTo("192.0.2.1");
    }

    @Test void outsideRequestContextAllowsNullMetadata() {
        assertThat(provider.current()).isEqualTo(new AuditContextProvider.AuditContext(null, null, null));
    }

    private void authenticate(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, java.util.List.of()));
    }
    private MockHttpServletRequest request(String forwarded, String remote, String agent) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (forwarded != null) request.addHeader("X-Forwarded-For", forwarded);
        if (agent != null) request.addHeader("User-Agent", agent);
        request.setRemoteAddr(remote);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }
}
