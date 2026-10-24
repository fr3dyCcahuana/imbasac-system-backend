package com.paulfernandosr.possystembackend.security.infrastructure;

import com.paulfernandosr.possystembackend.security.domain.Session;
import com.paulfernandosr.possystembackend.security.domain.AccessSessionId;
import com.paulfernandosr.possystembackend.security.infrastructure.adapter.output.RedisSessionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

// Explicit local test port only. Never read application profiles or flush a Redis database.
@EnabledIfEnvironmentVariable(named="IMBASAC_TEST_REDIS_PORT", matches="[0-9]+")
class RedisSessionRepositoryIntegrationTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private RedisSessionRepository sessions;
    private final String username = "cert-test-" + UUID.randomUUID();
    private final List<UUID> ids = new ArrayList<>();
    @BeforeEach void connect() {
        var config = new RedisStandaloneConfiguration("127.0.0.1", Integer.parseInt(System.getenv("IMBASAC_TEST_REDIS_PORT")));
        config.setDatabase(15);
        factory = new LettuceConnectionFactory(config); factory.afterPropertiesSet(); factory.start();
        redis = new StringRedisTemplate(factory); redis.afterPropertiesSet();
        sessions = new RedisSessionRepository(redis); ReflectionTestUtils.setField(sessions,"sessionTtl",60L);
    }
    @AfterEach void disconnect() {
        sessions.deleteByUsername(username);
        for (UUID id : ids) redis.delete(RedisConstants.SESSION_PREFIX+AccessSessionId.fromRefreshCredential(id));
        factory.destroy();
    }
    @Test void nextLoginInvalidatesOldAccessAndRefreshAndOldLogoutPreservesNewLogin() {
        UUID first = login(), second = login();
        assertThat(sessions.isActive(AccessSessionId.fromRefreshCredential(first),username)).isFalse();
        assertThat(sessions.findById(first)).isEmpty(); assertThat(sessions.refreshById(first)).isFalse();
        sessions.deleteById(first);
        assertThat(sessions.isActive(AccessSessionId.fromRefreshCredential(second),username)).isTrue();
        assertThat(sessions.isActive(AccessSessionId.fromRefreshCredential(second),"another-user")).isFalse();
    }
    @Test void orphanedOldSessionCannotDeleteOrExtendNewIndex() {
        UUID first = login(), second = login();
        redis.opsForValue().set(RedisConstants.SESSION_PREFIX+AccessSessionId.fromRefreshCredential(first),username,Duration.ofSeconds(60));
        redis.expire(RedisConstants.USER_PREFIX+username,Duration.ofSeconds(10));
        assertThat(sessions.refreshById(first)).isFalse();
        assertThat(redis.getExpire(RedisConstants.USER_PREFIX+username)).isLessThanOrEqualTo(10);
        sessions.deleteById(first); assertThat(sessions.isActive(AccessSessionId.fromRefreshCredential(second),username)).isTrue();
    }
    @Test void missingUserIndexFailsClosed() {
        UUID id=login(); redis.delete(RedisConstants.USER_PREFIX+username);
        assertThat(sessions.findById(id)).isEmpty(); assertThat(sessions.refreshById(id)).isFalse();
    }
    @Test void currentRefreshExtendsBothKeysAndLogoutRemovesBoth() {
        UUID id=login();
        redis.expire(RedisConstants.SESSION_PREFIX+AccessSessionId.fromRefreshCredential(id),Duration.ofSeconds(5));
        redis.expire(RedisConstants.USER_PREFIX+username,Duration.ofSeconds(5));
        assertThat(sessions.refreshById(id)).isTrue();
        assertThat(redis.getExpire(RedisConstants.SESSION_PREFIX+AccessSessionId.fromRefreshCredential(id))).isGreaterThan(50);
        assertThat(redis.getExpire(RedisConstants.USER_PREFIX+username)).isGreaterThan(50);
        sessions.deleteByUsername(username);
        assertThat(sessions.findById(id)).isEmpty();
        assertThat(redis.hasKey(RedisConstants.SESSION_PREFIX+AccessSessionId.fromRefreshCredential(id))).isFalse();
    }
    @Test void concurrentLoginsLeaveExactlyOneLiveSession() throws Exception {
        for(int i=0;i<24;i++) ids.add(UUID.randomUUID());
        try(var executor=Executors.newFixedThreadPool(6)) {
            var work=new ArrayList<Callable<Void>>();
            for(UUID id:ids) work.add(() -> { sessions.create(new Session(id,username)); return null; });
            for(var result:executor.invokeAll(work)) result.get();
        }
        List<UUID> active=ids.stream().filter(id -> sessions.isActive(AccessSessionId.fromRefreshCredential(id),username)).toList();
        assertThat(active).hasSize(1);
        for(UUID id:ids) if(!active.contains(id)) sessions.deleteById(id);
        assertThat(sessions.isActive(AccessSessionId.fromRefreshCredential(active.getFirst()),username)).isTrue();
    }
    @Test void expiredSessionCannotBeRefreshed() throws Exception {
        ReflectionTestUtils.setField(sessions,"sessionTtl",1L);
        UUID id=login(); Thread.sleep(1200);
        assertThat(sessions.findById(id)).isEmpty(); assertThat(sessions.refreshById(id)).isFalse();
    }
    private UUID login() { UUID id=UUID.randomUUID(); ids.add(id); sessions.create(new Session(id,username)); return id; }
}
