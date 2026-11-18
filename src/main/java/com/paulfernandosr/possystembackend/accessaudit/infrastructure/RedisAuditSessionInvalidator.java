package com.paulfernandosr.possystembackend.accessaudit.infrastructure;
import com.paulfernandosr.possystembackend.accessaudit.domain.port.AuditSessionInvalidator;
import com.paulfernandosr.possystembackend.security.infrastructure.adapter.output.RedisSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class RedisAuditSessionInvalidator implements AuditSessionInvalidator {
    private final RedisSessionRepository redis;
    @Override public void invalidate(String key,String username) { redis.invalidateAccessSession(key,username); }
}
