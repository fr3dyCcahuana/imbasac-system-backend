package com.paulfernandosr.possystembackend.security.domain.port.input;

import java.util.UUID;

public interface LogoutUseCase {
    void logout(UUID sessionId);
}
