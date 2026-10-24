package com.paulfernandosr.possystembackend.security.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.security.domain.Session;
import com.paulfernandosr.possystembackend.security.domain.AccessSessionId;
import com.paulfernandosr.possystembackend.security.domain.port.output.SessionRepository;
import com.paulfernandosr.possystembackend.security.infrastructure.RedisConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RedisSessionRepository implements SessionRepository {
    private final StringRedisTemplate stringRedisTemplate;
    @Value("${security.session.ttl}")
    private long sessionTtl;

    // Single-instance Redis: replace both indexes atomically. Older sessions must
    // never delete or extend the index belonging to a later login.
    private static final DefaultRedisScript<Long> CREATE = new DefaultRedisScript<>("""
            local previous = redis.call('GET', KEYS[1])
            if previous then redis.call('DEL', ARGV[4] .. previous) end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
            redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<String> READ = new DefaultRedisScript<>("""
            local username = redis.call('GET', KEYS[1])
            if not username then return nil end
            local userkey = ARGV[2] .. username
            if redis.call('GET', userkey) ~= ARGV[1] then return nil end
            if ARGV[3] ~= '0' then
                redis.call('EXPIRE', KEYS[1], ARGV[3])
                redis.call('EXPIRE', userkey, ARGV[3])
            end
            return username
            """, String.class);
    private static final DefaultRedisScript<Long> DELETE_ID = new DefaultRedisScript<>("""
            local username = redis.call('GET', KEYS[1])
            if not username then return 0 end
            local userkey = ARGV[2] .. username
            if redis.call('GET', userkey) == ARGV[1] then redis.call('DEL', userkey) end
            return redis.call('DEL', KEYS[1])
            """, Long.class);
    private static final DefaultRedisScript<Long> DELETE_USER = new DefaultRedisScript<>("""
            local session = redis.call('GET', KEYS[1])
            if session then redis.call('DEL', ARGV[1] .. session) end
            return redis.call('DEL', KEYS[1])
            """, Long.class);

    @Override
    public void create(Session session) {
        checkTtl();
        stringRedisTemplate.execute(CREATE,
                List.of(RedisConstants.USER_PREFIX + session.getUsername(), sessionKey(session.getId())),
                AccessSessionId.fromRefreshCredential(session.getId()), session.getUsername(), Long.toString(sessionTtl), RedisConstants.SESSION_PREFIX);
    }
    @Override
    public Optional<Session> findById(UUID sessionId) {
        return read(sessionId, 0).map(username -> new Session(sessionId, username));
    }
    @Override
    public boolean isActive(String accessSessionId, String username) {
        return readAccessSession(accessSessionId, 0).filter(username::equals).isPresent();
    }
    @Override
    public boolean refreshById(UUID sessionId) {
        checkTtl();
        return read(sessionId, sessionTtl).isPresent();
    }
    @Override
    public void deleteById(UUID sessionId) {
        stringRedisTemplate.execute(DELETE_ID, List.of(sessionKey(sessionId)), AccessSessionId.fromRefreshCredential(sessionId), RedisConstants.USER_PREFIX);
    }
    @Override
    public void deleteByUsername(String username) {
        stringRedisTemplate.execute(DELETE_USER, List.of(RedisConstants.USER_PREFIX + username), RedisConstants.SESSION_PREFIX);
    }
    private Optional<String> read(UUID sessionId, long ttl) {
        return readAccessSession(AccessSessionId.fromRefreshCredential(sessionId), ttl);
    }
    private Optional<String> readAccessSession(String accessSessionId, long ttl) {
        return Optional.ofNullable(stringRedisTemplate.execute(READ, List.of(RedisConstants.SESSION_PREFIX + accessSessionId),
                accessSessionId, RedisConstants.USER_PREFIX, Long.toString(ttl)));
    }
    private String sessionKey(UUID sessionId) {
        return RedisConstants.SESSION_PREFIX + AccessSessionId.fromRefreshCredential(sessionId);
    }
    private void checkTtl() {
        if (sessionTtl <= 0) throw new IllegalStateException("Session TTL must be positive");
    }
}
