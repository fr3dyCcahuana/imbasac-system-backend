package com.paulfernandosr.possystembackend.security.domain.port.output;

import com.paulfernandosr.possystembackend.security.domain.Session;

import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {
    void create(Session session);

    Optional<Session> findById(UUID sessionId);

    boolean isActive(String accessSessionId, String username);

    boolean refreshById(UUID sessionId);

    void deleteById(UUID sessionId);

    void deleteByUsername(String username);
}
