package com.paulfernandosr.possystembackend.security.application;

import com.paulfernandosr.possystembackend.security.domain.Session;
import com.paulfernandosr.possystembackend.security.domain.exception.InvalidSessionException;
import com.paulfernandosr.possystembackend.security.domain.port.input.RefreshSessionUseCase;
import com.paulfernandosr.possystembackend.security.domain.port.output.TokenGenerationPort;
import com.paulfernandosr.possystembackend.security.domain.port.output.SessionRepository;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshSessionService implements RefreshSessionUseCase {
    private final SessionRepository sessionRepository;
    private final TokenGenerationPort tokenGenerationPort;
    private final UserRepository userRepository;

    @Override
    public Session refreshSession(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new InvalidSessionException("Invalid session"));

        boolean enabled = userRepository.findByUsername(session.getUsername())
                .map(user -> user.isEnabled()).orElse(false);
        if (!enabled) {
            sessionRepository.deleteById(sessionId);
            throw new InvalidSessionException("Invalid session");
        }

        if (!sessionRepository.refreshById(sessionId)) {
            throw new InvalidSessionException("Invalid session");
        }

        session.setToken(tokenGenerationPort.generateToken(session.getUsername(), sessionId));

        return session;
    }
}
