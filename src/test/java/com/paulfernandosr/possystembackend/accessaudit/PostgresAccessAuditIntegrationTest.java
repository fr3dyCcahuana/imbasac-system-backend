package com.paulfernandosr.possystembackend.accessaudit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.accessaudit.application.AccessAuditService;
import com.paulfernandosr.possystembackend.accessaudit.domain.*;
import com.paulfernandosr.possystembackend.accessaudit.domain.AccessAuditModels.*;
import com.paulfernandosr.possystembackend.accessaudit.infrastructure.*;
import com.paulfernandosr.possystembackend.security.domain.*;
import com.paulfernandosr.possystembackend.security.domain.port.output.SessionRepository;
import com.paulfernandosr.possystembackend.security.infrastructure.adapter.output.RedisSessionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ACCESS_AUDIT_TEST_JDBC_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/access_audit_test")
@EnabledIfEnvironmentVariable(named="ACCESS_AUDIT_TEST_REDIS_PORT",matches="[0-9]{4,5}")
class PostgresAccessAuditIntegrationTest {
    private DriverManagerDataSource admin,data;
    private JdbcTemplate jdbc;
    private String schema,user,other;
    private LettuceConnectionFactory redisFactory;
    private RedisSessionRepository redis;
    private PostgresAccessAuditRepository store;
    private SessionRepository sessions;
    private AccessAuditService service;
    private final Metadata meta=new Metadata("127.0.0.1","Test browser",UUID.randomUUID());

    @BeforeEach void setup() throws Exception {
        String url=System.getenv("ACCESS_AUDIT_TEST_JDBC_URL");
        admin=new DriverManagerDataSource(url,"postgres",System.getenv("ACCESS_AUDIT_TEST_PASSWORD"));
        schema="audit_test_"+UUID.randomUUID().toString().replace("-","");
        user="audit_"+schema.substring(11,23); other=user+"_other";
        new JdbcTemplate(admin).execute("CREATE SCHEMA "+schema);
        data=new DriverManagerDataSource(url+"?currentSchema="+schema,"postgres",System.getenv("ACCESS_AUDIT_TEST_PASSWORD"));
        jdbc=new JdbcTemplate(data);
        jdbc.execute("CREATE TABLE roles(id BIGINT PRIMARY KEY,name TEXT); CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,role_id BIGINT,enabled BOOLEAN); CREATE TABLE permissions(id BIGSERIAL PRIMARY KEY,name TEXT,domain TEXT,description TEXT)");
        jdbc.update("INSERT INTO roles VALUES(1,'ADMINISTRATOR'); INSERT INTO users VALUES(1,?,1,TRUE),(2,?,1,TRUE)",user,other);
        migrate();
        redisFactory=new LettuceConnectionFactory("127.0.0.1",Integer.parseInt(System.getenv("ACCESS_AUDIT_TEST_REDIS_PORT")));
        redisFactory.afterPropertiesSet();
        redis=new RedisSessionRepository(new StringRedisTemplate(redisFactory)); ReflectionTestUtils.setField(redis,"sessionTtl",300);
        store=new PostgresAccessAuditRepository(JdbcClient.create(data));
        sessions=transactional(new AuditedSessionRepository(redis,store,new AuditRequestMetadata(),true,300),SessionRepository.class);
        service=transactional(new AccessAuditService(store,()->meta,true,new RedisAuditSessionInvalidator(redis)),AccessAuditService.class);
    }
    @AfterEach void cleanup() {
        if (redis!=null) { redis.deleteByUsername(user); redis.deleteByUsername(other); redis.deleteByUsername(user+"_renamed"); }
        if (redisFactory!=null) redisFactory.destroy();
        if (schema!=null && schema.matches("audit_test_[a-f0-9]{32}")) new JdbcTemplate(admin).execute("DROP SCHEMA "+schema+" CASCADE");
    }
    private <T> T transactional(T target,Class<T> type) {
        ProxyFactory proxy=new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(data),new AnnotationTransactionAttributeSource()));
        return type.cast(proxy.getProxy());
    }
    private Session session(String username) { return new Session(UUID.randomUUID(),username); }
    private String key(Session session) { return AccessSessionId.fromRefreshCredential(session.getId()); }
    private AuditQuery query() { LocalDate day=LocalDate.now(AuditQuery.LIMA); return AuditQuery.of(day,day,null,null,null,null,0,25); }
    private UUID auditId(Session session) { return jdbc.queryForObject("SELECT id FROM security_access_session WHERE session_key=?",UUID.class,key(session)); }
    private void migrate() throws Exception {
        jdbc.execute(new String(new ClassPathResource("db/scripts/20261029_access_audit.sql").getInputStream().readAllBytes(),StandardCharsets.UTF_8));
    }
    @Test void recordsGrantedSessionAndPublicViewsNeverExposeSessionCredentials() throws Exception {
        Session one=session(user); sessions.create(one);
        assertThat(sessions.isActive(key(one),user)).isTrue();
        Summary summary=service.summary(query()); assertThat(summary.grantedLogins()).isEqualTo(1); assertThat(summary.distinctUsers()).isEqualTo(1);
        String json=new ObjectMapper().findAndRegisterModules().writeValueAsString(service.events(query()));
        assertThat(json).contains("LOGIN_GRANTED",user,"ADMINISTRATOR").doesNotContain(key(one),one.getId().toString(),"session_key");
        assertThat(service.status().firstAvailableEvent()).isNotNull();
    }
    @Test void migrationIsRepeatableAndDoesNotAssignAnyPermission() throws Exception {
        migrate(); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM permissions",Integer.class)).isEqualTo(3);
        assertThat(store.firstEvent()).isNull();
    }
    @Test void twoConcurrentLoginsRetainExactlyOneCurrentSessionAndBothEvents() throws Exception {
        Session a=session(user),b=session(user); CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Void> first=()->{ready.countDown();go.await(5,TimeUnit.SECONDS);sessions.create(a);return null;};
            Callable<Void> second=()->{ready.countDown();go.await(5,TimeUnit.SECONDS);sessions.create(b);return null;};
            Future<Void> fa=pool.submit(first),fb=pool.submit(second); assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();fa.get(10,TimeUnit.SECONDS);fb.get(10,TimeUnit.SECONDS);
        }
        assertThat(sessions.isActive(key(a),user) ^ sessions.isActive(key(b),user)).isTrue();
        assertThat(service.summary(query()).grantedLogins()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_access_event WHERE event_type='SESSION_REPLACED'",Integer.class)).isEqualTo(1);
    }
    @Test void revokedSessionCannotAuthenticateOrRefreshAndOtherUserIsUnaffected() {
        Session one=session(user),two=session(other);sessions.create(one);sessions.create(two);
        UUID id=auditId(one);service.revoke(id,"admin","Administrative revocation");service.revoke(id,"admin","Idempotent revocation retry");
        assertThat(sessions.isActive(key(one),user)).isFalse(); assertThat(sessions.findById(one.getId())).isEmpty();
        assertThat(sessions.refreshById(one.getId())).isFalse(); assertThat(redis.isActive(key(one),user)).isFalse();
        assertThat(sessions.isActive(key(two),other)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_access_event WHERE event_type='SESSION_REVOKED'",Integer.class)).isEqualTo(1);
    }
    @Test void lateLogoutAndLateRedisInvalidationNeverDeleteTheNewSession() {
        Session old=session(user),current=session(user); sessions.create(old);sessions.create(current);
        sessions.logout(old.getId()); redis.invalidateAccessSession(key(old),user);
        assertThat(sessions.isActive(key(current),user)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_access_event WHERE event_type='LOGOUT'",Integer.class)).isZero();
    }
    @Test void logoutAndServerInvalidationHaveDifferentMeanings() {
        Session one=session(user),two=session(other);sessions.create(one);sessions.create(two);
        sessions.logout(one.getId());sessions.deleteById(two.getId());
        assertThat(jdbc.queryForObject("SELECT state FROM security_access_session WHERE username=?",String.class,user)).isEqualTo("LOGGED_OUT");
        assertThat(jdbc.queryForObject("SELECT state FROM security_access_session WHERE username=?",String.class,other)).isEqualTo("INVALIDATED");
    }
    @Test void failedRedisCreationRollsBackTheGrantedEventAndLedger() {
        RedisSessionRepository unavailable=mock(RedisSessionRepository.class);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test only")).when(unavailable).create(any());
        var coordinator=transactional(new AuditedSessionRepository(unavailable,store,new AuditRequestMetadata(),true,300),SessionRepository.class);
        assertThatThrownBy(()->coordinator.create(session(user))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_access_session",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_access_event",Integer.class)).isZero();
    }
    @Test void localDayBoundariesAndHourlyCountsAreAccurate() {
        store.record("LOGIN_GRANTED","GRANTED","BACKEND",user,null,null,null,"TEST",meta,false);
        store.record("LOGIN_GRANTED","GRANTED","BACKEND",other,null,null,null,"TEST",meta,false);
        jdbc.update("UPDATE security_access_event SET occurred_at='2026-10-29 04:59:59+00',last_occurred_at='2026-10-29 04:59:59+00' WHERE username=?",user);
        jdbc.update("UPDATE security_access_event SET occurred_at='2026-10-29 05:00:00+00',last_occurred_at='2026-10-29 05:00:00+00' WHERE username=?",other);
        var day=AuditQuery.of(LocalDate.of(2026,10,29),LocalDate.of(2026,10,29),null,null,null,null,0,25);
        assertThat(service.summary(day).grantedLogins()).isEqualTo(1);assertThat(service.summary(day).hours().getFirst().hour()).isZero();
        assertThat(service.summary(day).outsideBusinessHours()).isEqualTo(1);
    }
    @Test void repeatedDenialsAreCountedWithoutPretendingAnAttemptedAccountIsVerified() {
        service.loginRejected("unknown",new IllegalArgumentException());service.loginRejected("unknown",new IllegalArgumentException());
        var page=store.events(query(),25,0);assertThat(page.total()).isEqualTo(1);
        assertThat(page.items().getFirst().occurrences()).isEqualTo(2);assertThat(page.items().getFirst().username()).isNull();
        assertThat(service.summary(query()).rejectedAttempts()).isEqualTo(2);
    }
    @Test void exactUsernameFiltersAreParameterizedAndCannotReadOtherAccounts() {
        sessions.create(session(user));sessions.create(session(other));
        var q=query();var filtered=new AuditQuery(q.from(),q.until(),"' OR 1=1 --",null,null,null,0,25);
        assertThat(service.events(filtered).total()).isZero();
    }
    @Test void csvUsesLimaNeutralizesContentAndRecordsAnExport() {
        store.record("LOGIN_GRANTED","GRANTED","BACKEND",user,null,null,null,"=FORMULA",meta,false);
        String csv=new String(service.export(query(),"admin"),StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFFecha Lima").contains("'="+"FORMULA");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_access_event WHERE event_type='AUDIT_EXPORTED'",Integer.class)).isEqualTo(1);
    }
    @Test void expiredSessionsAreNotDisplayedAsActiveAndCannotBeRenewed() {
        Session one=session(user);sessions.create(one);
        jdbc.update("UPDATE security_access_session SET started_at=CURRENT_TIMESTAMP-INTERVAL '2 hours',expires_at=CURRENT_TIMESTAMP-INTERVAL '1 hour' WHERE username=?",user);
        assertThat(sessions.refreshById(one.getId())).isFalse();assertThat(sessions.isActive(key(one),user)).isFalse();
        assertThat(service.sessions(query(),"EXPIRED").items().getFirst().state()).isEqualTo("EXPIRED");
        assertThat(service.summary(query()).activeSessionsNow()).isZero();
    }
    @Test void springChoosesTheLedgerOnlyWhenExplicitlyEnabled() {
        for (boolean enabled:new boolean[]{false,true}) {
            try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
                context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test",Map.of("security.access-audit.enabled",Boolean.toString(enabled),"security.session.ttl","300")));
                context.registerBean(RedisSessionRepository.class,()->redis);
                context.registerBean(com.paulfernandosr.possystembackend.accessaudit.domain.port.AccessAuditRepository.class,()->store);
                context.register(AuditRequestMetadata.class,AuditedSessionRepository.class);context.refresh();
                if (enabled) assertThat(context.getBean(SessionRepository.class)).isInstanceOf(AuditedSessionRepository.class);
                else assertThat(context.getBean(SessionRepository.class)).isSameAs(redis);
            }
        }
    }
    @Test void renamingAnAccountKeepsHistoricalIdentityAndDoesNotDoubleCountThePerson() {
        sessions.create(session(user));jdbc.update("UPDATE users SET username=? WHERE id=1",user+"_renamed");
        sessions.create(session(user+"_renamed"));
        assertThat(service.summary(query()).distinctUsers()).isEqualTo(1);
        assertThat(service.summary(query()).activeSessionsNow()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT user_id) FROM security_access_event WHERE event_type='LOGIN_GRANTED'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_access_event WHERE event_type='SESSION_REPLACED'",Integer.class)).isEqualTo(1);
    }
    @Test void concurrentRevocationWinsOverARefreshThatAlreadyCheckedTheOldState() throws Exception {
        Session one=session(user);sessions.create(one);
        CountDownLatch locked=new CountDownLatch(1),extending=new CountDownLatch(1),commit=new CountDownLatch(1);
        var observed=spy(store);
        doAnswer(call->{extending.countDown();return call.callRealMethod();}).when(observed).extend(anyString(),anyLong());
        var renewing=transactional(new AuditedSessionRepository(redis,observed,new AuditRequestMetadata(),true,300),SessionRepository.class);
        try(var pool=Executors.newFixedThreadPool(2)) {
            try {
                Future<?> revoke=pool.submit(()->new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(data)).execute(status->{
                    store.revoke(auditId(one),"admin","Concurrent revocation",meta);locked.countDown();
                    try { if (!commit.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Test synchronization timed out"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException(e); }
                    return null;
                }));
                assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
                Future<Boolean> refresh=pool.submit(()->renewing.refreshById(one.getId()));
                assertThat(extending.await(5,TimeUnit.SECONDS)).isTrue();commit.countDown();revoke.get(10,TimeUnit.SECONDS);
                assertThat(refresh.get(10,TimeUnit.SECONDS)).isFalse();assertThat(sessions.isActive(key(one),user)).isFalse();
            } finally { commit.countDown(); }
        }
    }
    @Test void failedRedisInvalidationNeverCommitsAFalselySuccessfulRevocation() {
        Session one=session(user);sessions.create(one);
        var failing=transactional(new AccessAuditService(store,()->meta,true,(key,username)->{
            throw new org.springframework.dao.DataAccessResourceFailureException("Test Redis outage");
        }),AccessAuditService.class);
        assertThatThrownBy(()->failing.revoke(auditId(one),"admin","Administrative revocation")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT state FROM security_access_session WHERE id=?",String.class,auditId(one))).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_access_event WHERE event_type='SESSION_REVOKED'",Integer.class)).isZero();
    }
    @Test void aLargeRealExportIsRejectedInsteadOfTruncated() {
        jdbc.execute("INSERT INTO security_access_event(id,event_type,outcome,source,reason,correlation_id) SELECT md5('audit-limit-'||i)::uuid,'ACCESS_DENIED','REJECTED','BACKEND','TEST',md5('correlation-'||i)::uuid FROM generate_series(1,5001) i");
        assertThatThrownBy(()->service.export(query(),"admin")).isInstanceOfSatisfying(AuditException.class,e->assertThat(e.status()).isEqualTo(409));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_access_event WHERE event_type='AUDIT_EXPORTED'",Integer.class)).isZero();
    }
}
