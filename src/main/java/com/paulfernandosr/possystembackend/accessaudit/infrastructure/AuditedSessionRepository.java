package com.paulfernandosr.possystembackend.accessaudit.infrastructure;

import com.paulfernandosr.possystembackend.accessaudit.domain.AuditException;
import com.paulfernandosr.possystembackend.accessaudit.domain.port.AccessAuditRepository;
import com.paulfernandosr.possystembackend.security.domain.AccessSessionId;
import com.paulfernandosr.possystembackend.security.domain.Session;
import com.paulfernandosr.possystembackend.security.domain.port.output.SessionRepository;
import com.paulfernandosr.possystembackend.security.infrastructure.adapter.output.RedisSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

/** Redis remains necessary; the enabled ledger also denies revoked/unobserved sessions. */
@Repository
@Primary
@ConditionalOnProperty(name="security.access-audit.enabled",havingValue="true")
public class AuditedSessionRepository implements SessionRepository {
    private final RedisSessionRepository redis;
    private final AccessAuditRepository audit;
    private final AuditRequestMetadata metadata;
    private final boolean enabled;
    private final long ttl;

    public AuditedSessionRepository(RedisSessionRepository redis, AccessAuditRepository audit, AuditRequestMetadata metadata,
            @Value("${security.access-audit.enabled:false}") boolean enabled,
            @Value("${security.session.ttl}") long ttl) {
        this.redis=redis; this.audit=audit; this.metadata=metadata; this.enabled=enabled; this.ttl=ttl;
    }
    @Override @Transactional(timeout=5)
    public void create(Session session) {
        if (!enabled) { redis.create(session); return; }
        if (ttl<=0) throw AuditException.unavailable();
        audit.lockUser(session.getUsername());
        audit.start(key(session.getId()),session.getUsername(),ttl,metadata.current());
        redis.create(session);
        // A failed DB commit may leave Redis populated, but no matching durable row: access stays denied.
    }
    @Override public Optional<Session> findById(UUID id) {
        var session=redis.findById(id);
        if (!enabled) return session;
        return session.filter(value->audit.active(key(id),value.getUsername()));
    }
    @Override public boolean isActive(String key, String username) {
        if (!enabled) return redis.isActive(key,username);
        if (!audit.active(key,username) || !redis.isActive(key,username)) return false;
        audit.touch(key); return true;
    }
    @Override @Transactional(timeout=5)
    public boolean refreshById(UUID id) {
        if (!enabled) return redis.refreshById(id);
        var session=redis.findById(id);
        if (session.isEmpty()) return false;
        audit.lockUser(session.get().getUsername());
        if (!audit.active(key(id),session.get().getUsername()) || !redis.refreshById(id)) return false;
        return audit.extend(key(id),ttl);
    }
    @Override @Transactional(timeout=5)
    public void deleteById(UUID id) {
        if (enabled) audit.end(key(id),"SESSION_INVALIDATED","SESSION_INVALIDATED_BY_SERVER",metadata.current());
        redis.deleteById(id);
    }
    @Override @Transactional(timeout=5)
    public void logout(UUID id) {
        if (enabled) audit.end(key(id),"LOGOUT","LOGOUT_CONFIRMED",metadata.current());
        redis.deleteById(id);
    }
    @Override @Transactional(timeout=5)
    public void deleteByUsername(String username) {
        if (enabled) { audit.lockUser(username); audit.endUser(username,metadata.current()); }
        redis.deleteByUsername(username);
    }
    private String key(UUID id) { return AccessSessionId.fromRefreshCredential(id); }
}
