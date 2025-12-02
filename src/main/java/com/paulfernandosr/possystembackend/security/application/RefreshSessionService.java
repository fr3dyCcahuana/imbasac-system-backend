package com.paulfernandosr.possystembackend.security.application;

import com.paulfernandosr.possystembackend.security.domain.Session;
import com.paulfernandosr.possystembackend.security.domain.exception.InvalidSessionException;
import com.paulfernandosr.possystembackend.security.domain.port.input.RefreshSessionUseCase;
import com.paulfernandosr.possystembackend.security.domain.port.output.TokenGenerationPort;
import com.paulfernandosr.possystembackend.security.domain.port.output.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshSessionService implements RefreshSessionUseCase {
    private final SessionRepository sessionRepository;
    private final TokenGenerationPort tokenGenerationPort;

    @Override
    public Session refreshSession(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new InvalidSessionException("Invalid session with identification: " + sessionId));

        sessionRepository.refreshById(sessionId);

        session.setToken(tokenGenerationPort.generateToken(session.getUsername()));

        return session;
    }
}
