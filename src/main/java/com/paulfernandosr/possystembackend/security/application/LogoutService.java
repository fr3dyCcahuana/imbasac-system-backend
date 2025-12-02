package com.paulfernandosr.possystembackend.security.application;

import com.paulfernandosr.possystembackend.security.domain.port.input.LogoutUseCase;
import com.paulfernandosr.possystembackend.security.domain.port.output.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LogoutService implements LogoutUseCase {
    private final SessionRepository sessionRepository;

    @Override
    public void logout(UUID sessionId) {
        sessionRepository.deleteById(sessionId);
    }
}
