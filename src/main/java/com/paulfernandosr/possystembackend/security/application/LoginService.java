package com.paulfernandosr.possystembackend.security.application;

import com.paulfernandosr.possystembackend.security.domain.exception.InvalidCredentialsException;
import com.paulfernandosr.possystembackend.security.domain.Session;
import com.paulfernandosr.possystembackend.security.domain.port.input.LoginUseCase;
import com.paulfernandosr.possystembackend.security.domain.port.output.AuthenticationPort;
import com.paulfernandosr.possystembackend.security.domain.port.output.TokenGenerationPort;
import com.paulfernandosr.possystembackend.security.domain.port.output.SessionRepository;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.exception.UserNotFoundException;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {
    private final AuthenticationPort authenticationPort;
    private final TokenGenerationPort tokenGenerationPort;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;

    @Override
    public Session login(String username, String password) {
        boolean isAuthenticated = authenticationPort.authenticate(username, password);

        if (!isAuthenticated) {
            throw new InvalidCredentialsException("User credentials are invalid");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with identification: " + username));

        if (!user.isEnabled()) {
            throw new InvalidCredentialsException("User credentials are invalid");
        }

        if (user.requiresCashSession() && user.isNotOnRegister()) {
            throw new InvalidCredentialsException("User requires cash session and is not on register with identification: " + username);
        }

        UUID sessionId = UUID.randomUUID();
        Session session = Session.builder()
                .id(sessionId)
                .username(username)
                .token(tokenGenerationPort.generateToken(username, sessionId))
                .build();

        sessionRepository.create(session);

        return session;
    }
}
