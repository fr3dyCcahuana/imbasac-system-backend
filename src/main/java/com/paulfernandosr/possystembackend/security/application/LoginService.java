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

        if (user.isCashier() && user.isNotOnRegister()) {
            throw new InvalidCredentialsException("User is cashier and is not on register with identification: " + username);
        }

        sessionRepository.deleteByUsername(username);

        Session session = Session.builder()
                .id(UUID.randomUUID())
                .username(username)
                .token(tokenGenerationPort.generateToken(username))
                .build();

        sessionRepository.create(session);

        return session;
    }
}
