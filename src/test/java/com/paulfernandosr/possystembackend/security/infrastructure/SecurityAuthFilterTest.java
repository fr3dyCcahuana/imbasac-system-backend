package com.paulfernandosr.possystembackend.security.infrastructure;

import com.paulfernandosr.possystembackend.security.domain.port.output.SessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityAuthFilterTest {
    private final JsonWebTokenUtils tokens = mock(JsonWebTokenUtils.class);
    private final UserDetailsService users = mock(UserDetailsService.class);
    private final SessionRepository sessions = mock(SessionRepository.class);
    private final SecurityAuthFilter filter = new SecurityAuthFilter(tokens, users, sessions);
    private final String session = "a".repeat(64);
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void authenticatesOnlyExactLiveSession() throws Exception {
        configure(true);
        when(sessions.isActive(session, "client")).thenReturn(true);
        filter.doFilter(bearer(), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("client");
        verify(sessions).isActive(session, "client");
    }
    @Test void rejectsOldSessionEvenWhenUserHasAnotherLogin() throws Exception {
        configure(true);
        filter.doFilter(bearer(), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @Test void rejectsDisabledAccount() throws Exception {
        configure(false);
        filter.doFilter(bearer(), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(sessions);
    }
    @Test void queryCredentialCannotAuthenticateAnOrdinaryEndpoint() throws Exception {
        var request = new MockHttpServletRequest("GET", "/users/me");
        request.setServletPath("/users/me"); request.addParameter("token", "secret");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        verifyNoInteractions(tokens, users, sessions);
    }
    @Test void temporarySseQueryCompatibilityIsRestrictedToGetStream() throws Exception {
        configure(true); when(sessions.isActive(session, "client")).thenReturn(true);
        var request = new MockHttpServletRequest("GET", "/community-notifications/stream");
        request.setServletPath("/community-notifications/stream"); request.addParameter("token", "secret");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }
    @Test void failsClosedWhenSessionStoreIsUnavailable() throws Exception {
        configure(true); when(sessions.isActive(session, "client")).thenThrow(new IllegalStateException("store offline"));
        filter.doFilter(bearer(), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    private void configure(boolean enabled) {
        when(tokens.parseAccessToken("secret")).thenReturn(new JsonWebTokenUtils.AccessTokenIdentity("client", session));
        when(users.loadUserByUsername("client")).thenReturn(User.withUsername("client").password("unused")
                .disabled(!enabled).authorities("READ").build());
    }
    private MockHttpServletRequest bearer() {
        var request = new MockHttpServletRequest("GET", "/users/me");
        request.addHeader("Authorization", "Bearer secret"); return request;
    }
}
