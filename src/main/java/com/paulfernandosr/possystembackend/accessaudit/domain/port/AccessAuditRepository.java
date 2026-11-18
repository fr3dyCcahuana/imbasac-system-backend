package com.paulfernandosr.possystembackend.accessaudit.domain.port;

import com.paulfernandosr.possystembackend.accessaudit.domain.AccessAuditModels.*;
import com.paulfernandosr.possystembackend.accessaudit.domain.AuditQuery;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccessAuditRepository {
    void lockUser(String username);
    void start(String sessionKey, String username, long ttl, Metadata metadata);
    boolean active(String sessionKey, String username);
    void touch(String sessionKey);
    boolean extend(String sessionKey, long ttl);
    void end(String sessionKey, String type, String reason, Metadata metadata);
    void endUser(String username, Metadata metadata);
    void record(String type, String outcome, String source, String username, String attempted,
                String sessionKey, String actor, String reason, Metadata metadata, boolean aggregate);
    Page<Event> events(AuditQuery query, int limit, int offset);
    Optional<Event> event(UUID id);
    Page<SessionRow> sessions(AuditQuery query, String state);
    Summary summary(AuditQuery query);
    boolean revoke(UUID id, String actor, String reason, Metadata metadata);
    Optional<SessionReference> sessionReference(UUID id);
    Instant firstEvent();
    default boolean authenticationContextReady() { return false; }
    default Optional<String> sessionRejectionReason(String key) { return Optional.empty(); }
}
