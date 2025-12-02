package com.paulfernandosr.possystembackend.security.domain.port.output;

import com.paulfernandosr.possystembackend.security.domain.Session;

import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {
    void create(Session session);

    Optional<Session> findById(UUID sessionId);

    boolean existsByUsername(String username);

    void refreshById(UUID sessionId);

    void deleteById(UUID sessionId);

    void deleteByUsername(String username);
}
