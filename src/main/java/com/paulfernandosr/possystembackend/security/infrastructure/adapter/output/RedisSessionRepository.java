package com.paulfernandosr.possystembackend.security.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.security.domain.Session;
import com.paulfernandosr.possystembackend.security.domain.port.output.SessionRepository;
import com.paulfernandosr.possystembackend.security.infrastructure.RedisConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RedisSessionRepository implements SessionRepository {
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${security.session.ttl}")
    private long sessionTtl;

    @Override
    public void create(Session session) {
        stringRedisTemplate.opsForValue().set(RedisConstants.SESSION_PREFIX + session.getId().toString(), session.getUsername(), Duration.ofSeconds(sessionTtl));
        stringRedisTemplate.opsForValue().set(RedisConstants.USER_PREFIX + session.getUsername(), session.getId().toString(), Duration.ofSeconds(sessionTtl));
    }

    @Override
    public Optional<Session> findById(UUID sessionId) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(RedisConstants.SESSION_PREFIX + sessionId.toString()))
                .map(username -> new Session(sessionId, username));
    }

    @Override
    public boolean existsByUsername(String username) {
        return stringRedisTemplate.hasKey(RedisConstants.USER_PREFIX + username);
    }

    @Override
    public void refreshById(UUID sessionId) {
        Optional.ofNullable(stringRedisTemplate.opsForValue().getAndExpire(RedisConstants.SESSION_PREFIX + sessionId.toString(), Duration.ofSeconds(sessionTtl)))
                .ifPresent(username -> stringRedisTemplate.expire(RedisConstants.USER_PREFIX + username, Duration.ofSeconds(sessionTtl)));
    }

    @Override
    public void deleteById(UUID sessionId) {
        Optional.ofNullable(stringRedisTemplate.opsForValue().getAndDelete(RedisConstants.SESSION_PREFIX + sessionId.toString()))
                .ifPresent(username -> stringRedisTemplate.delete(RedisConstants.USER_PREFIX + username));
    }

    @Override
    public void deleteByUsername(String username) {
        Optional.ofNullable(stringRedisTemplate.opsForValue().getAndDelete(RedisConstants.USER_PREFIX + username))
                .ifPresent(sessionId -> stringRedisTemplate.delete(RedisConstants.SESSION_PREFIX + sessionId));
    }
}
