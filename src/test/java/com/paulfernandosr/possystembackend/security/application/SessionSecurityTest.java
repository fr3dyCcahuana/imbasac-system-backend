package com.paulfernandosr.possystembackend.security.application;

import com.paulfernandosr.possystembackend.security.domain.Session;
import com.paulfernandosr.possystembackend.security.domain.Token;
import com.paulfernandosr.possystembackend.security.domain.exception.InvalidCredentialsException;
import com.paulfernandosr.possystembackend.security.domain.exception.InvalidSessionException;
import com.paulfernandosr.possystembackend.security.domain.port.output.*;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionSecurityTest {
    private final SessionRepository sessions = mock(SessionRepository.class);
    private final TokenGenerationPort tokens = mock(TokenGenerationPort.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuthenticationPort authentication = mock(AuthenticationPort.class);
    private final RefreshSessionService refresh = new RefreshSessionService(sessions, tokens, users);
    private final UUID id = UUID.randomUUID();
    @Test void loginGeneratesTokenForTheSessionItStores() {
        when(authentication.authenticate("client", "password")).thenReturn(true);
        when(users.findByUsername("client")).thenReturn(Optional.of(User.builder().username("client").enabled(true).build()));
        var result = new LoginService(authentication,tokens,sessions,users).login("client", "password");
        verify(tokens).generateToken("client", result.getId()); verify(sessions).create(result);
        verify(sessions, never()).deleteByUsername(anyString());
    }
    @Test void disabledAccountCannotLoginEvenIfAuthenticationRaced() {
        when(authentication.authenticate("client", "password")).thenReturn(true);
        when(users.findByUsername("client")).thenReturn(Optional.of(User.builder().enabled(false).build()));
        assertThatThrownBy(() -> new LoginService(authentication,tokens,sessions,users).login("client", "password"))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(sessions, tokens);
    }
    @Test void disabledOrDeletedAccountCannotRefresh() {
        for (Optional<User> user : java.util.List.of(Optional.of(User.builder().enabled(false).build()), Optional.<User>empty())) {
            when(sessions.findById(id)).thenReturn(Optional.of(new Session(id,"client")));
            when(users.findByUsername("client")).thenReturn(user);
            assertThatThrownBy(() -> refresh.refreshSession(id)).isInstanceOf(InvalidSessionException.class)
                    .hasMessage("Invalid session");
        }
        verify(sessions, times(2)).deleteById(id); verifyNoInteractions(tokens);
    }
    @Test void logoutOrNewLoginDuringRefreshCannotReanimateSession() {
        enabledSession(); when(sessions.refreshById(id)).thenReturn(false);
        assertThatThrownBy(() -> refresh.refreshSession(id)).isInstanceOf(InvalidSessionException.class);
        verifyNoInteractions(tokens);
    }
    @Test void refreshKeepsExactSessionIdentity() {
        enabledSession(); when(sessions.refreshById(id)).thenReturn(true);
        when(tokens.generateToken("client",id)).thenReturn(new Token("new", "Bearer",300));
        assertThat(refresh.refreshSession(id).getToken().value()).isEqualTo("new");
    }
    private void enabledSession() {
        when(sessions.findById(id)).thenReturn(Optional.of(new Session(id,"client")));
        when(users.findByUsername("client")).thenReturn(Optional.of(User.builder().enabled(true).build()));
    }
}
