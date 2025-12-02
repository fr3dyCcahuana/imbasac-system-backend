package com.paulfernandosr.possystembackend.security.domain.port.input;

import com.paulfernandosr.possystembackend.security.domain.Session;

import java.util.UUID;

public interface RefreshSessionUseCase {
    Session refreshSession(UUID sessionId);
}
