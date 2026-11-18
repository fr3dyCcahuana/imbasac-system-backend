package com.paulfernandosr.possystembackend.accesscontrol;

import com.paulfernandosr.possystembackend.accesscontrol.domain.*;
import com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlModels.*;
import com.paulfernandosr.possystembackend.accesscontrol.infrastructure.PostgresAccessControlRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ACCESS_CONTROL_TEST_JDBC_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/access_control_test")
class PostgresAccessControlIntegrationTest {
    private DriverManagerDataSource admin;
    private com.zaxxer.hikari.HikariDataSource data;
    private String schema;
    private JdbcTemplate jdbc;
    private PostgresAccessControlRepository store;
    private final MutableClock clock=new MutableClock(Instant.parse("2026-01-31T17:00:00Z"));
    private UUID pc1,pc2,pc3;
    @BeforeEach void setup() throws Exception {
        String url=System.getenv("ACCESS_CONTROL_TEST_JDBC_URL");
        admin=new DriverManagerDataSource(url,"postgres",System.getenv("ACCESS_CONTROL_TEST_PASSWORD"));
        schema="access_control_test_"+UUID.randomUUID().toString().replace("-","");
        new JdbcTemplate(admin).execute("CREATE SCHEMA "+schema);
        data=new com.zaxxer.hikari.HikariDataSource();
        data.setJdbcUrl(url+"?currentSchema="+schema); data.setUsername("postgres");data.setPassword(System.getenv("ACCESS_CONTROL_TEST_PASSWORD"));
        data.setMaximumPoolSize(12);data.setMinimumIdle(0);data.setConnectionTimeout(10000);
        jdbc=new JdbcTemplate(data);
        jdbc.execute("CREATE TABLE roles(id BIGINT PRIMARY KEY,name TEXT); CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT UNIQUE,role_id BIGINT,enabled BOOLEAN); CREATE TABLE permissions(id BIGSERIAL PRIMARY KEY,name TEXT,domain TEXT,description TEXT)");
        jdbc.execute("INSERT INTO roles VALUES(1,'CLIENTE'),(2,'ADMINISTRATOR'); INSERT INTO users VALUES(1,'cliente_uno',1,TRUE),(2,'cliente_dos',1,TRUE),(3,'admin',2,TRUE)");
        migrate("20261029_device_access_foundation.sql"); migrate("20261030_access_control.sql"); migrate("20261112_daily_access_strikes.sql");
        store=new PostgresAccessControlRepository(JdbcClient.create(data),new DataSourceTransactionManager(data),clock);
        pc1=device("Compartida"); pc2=device("Segunda"); pc3=device("Tercera");
    }
    @AfterEach void cleanup() {
        if(data!=null) data.close();
        if(schema!=null&&schema.matches("access_control_test_[a-f0-9]{32}")) new JdbcTemplate(admin).execute("DROP SCHEMA "+schema+" CASCADE");
    }
    private void migrate(String name) throws Exception { jdbc.execute(new String(new ClassPathResource("db/scripts/"+name).getInputStream().readAllBytes(),StandardCharsets.UTF_8)); }
    private UUID device(String name) { UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO security_device(id,display_name,platform,status) VALUES(?,?,'WINDOWS','ACTIVE')",id,name); return id; }
    private Decision strike(long user,UUID attempt,String origin) { return store.recordUnauthorizedDevice(user,null,attempt,3,origin,60); }
    private void reachThreshold() { for(int i=0;i<3;i++) { strike(1,UUID.randomUUID(),"a".repeat(64)); clock.advance(Duration.ofSeconds(61)); } }
    @Test void repeatMigrationKeepsDataAndCreatesOnlyExplicitPermissionDefinitions() throws Exception {
        store.bind(1,pc1,"admin","Primera computadora aprobada"); migrate("20261030_access_control.sql");
        assertThat(store.isDeviceAuthorized(1,pc1)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM permissions",Integer.class)).isEqualTo(3);
    }
    @Test void sharedPcAndAdministrativeQuotaExceptionPreserveIndividualAuthorizations() {
        store.bind(1,pc1,"admin","Equipo compartido aprobado"); store.bind(2,pc1,"admin","Equipo compartido aprobado");
        assertThatThrownBy(()->store.bind(1,pc2,"admin","Segunda computadora solicitada")).isInstanceOf(AccessControlException.class);
        store.setQuota(1,2,"admin","Excepcion administrativa aprobada"); store.bind(1,pc2,"admin","Segunda computadora aprobada");
        assertThat(store.bindings(1)).hasSize(2); assertThat(store.bindings(2)).hasSize(1);
        assertThatThrownBy(()->store.setQuota(1,1,"admin","Reducir cupo aprobado")).isInstanceOf(AccessControlException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE security_user_device_quota SET device_quota=1 WHERE user_id=1")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM security_user_device_quota WHERE user_id=1")).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void concurrentBindingsCannotExceedDefaultQuota() throws Exception {
        List<Boolean> outcomes=concurrently(List.of(()->bindOutcome(pc1),()->bindOutcome(pc2)));
        assertThat(outcomes.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_user_device_binding WHERE user_id=1 AND revoked_at IS NULL",Integer.class)).isEqualTo(1);
    }
    @Test void databaseTriggerAlsoRejectsConcurrentWritersBypassingTheService() throws Exception {
        List<Boolean> outcomes=concurrently(List.of(()->rawBindOutcome(pc1),()->rawBindOutcome(pc2)));
        assertThat(outcomes.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
    }
    @Test void unbindingOneClientDoesNotRevokeTheSharedPcOrOtherClient() {
        store.bind(1,pc1,"admin","Equipo compartido aprobado"); store.bind(2,pc1,"admin","Equipo compartido aprobado");
        store.unbind(1,store.bindings(1).getFirst().id(),"admin","Autorizacion individual retirada");
        assertThat(store.isDeviceAuthorized(1,pc1)).isFalse(); assertThat(store.isDeviceAuthorized(2,pc1)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM security_device WHERE id=?",String.class,pc1)).isEqualTo("ACTIVE");
    }
    @Test void inactivePcCannotBeBoundAndDisabledPcNeverAuthorizes() {
        jdbc.update("UPDATE security_device SET status='PENDING' WHERE id=?",pc2);
        assertThatThrownBy(()->store.bind(1,pc2,"admin","No omitir aprobacion de equipo")).isInstanceOf(AccessControlException.class);
        store.bind(1,pc1,"admin","Equipo validado aprobado"); jdbc.update("UPDATE security_device SET status='DISABLED' WHERE id=?",pc1);
        assertThat(store.isDeviceAuthorized(1,pc1)).isFalse();
    }
    @Test void duplicateRequestsAndRapidRetriesProduceOnlyOneIncidentIncludingLaterReplays() {
        UUID first=UUID.randomUUID(),collapsed=UUID.randomUUID();
        assertThat(strike(1,first,null).strikes()).isEqualTo(1);
        assertThat(strike(1,first,null).strikes()).isEqualTo(1);
        assertThat(strike(1,collapsed,null).strikes()).isEqualTo(1);
        clock.advance(Duration.ofSeconds(61));
        assertThat(strike(1,collapsed,null).strikes()).isEqualTo(1);
        assertThat(strike(1,UUID.randomUUID(),null).strikes()).isEqualTo(2);
    }
    @Test void dailyCountResetsAtLimaMidnightWithoutErasingHistoryOrChangingTheSessionCycle() {
        clock.value=Instant.parse("2026-02-01T04:59:50Z");
        UUID originalAttempt=UUID.randomUUID(); strike(1,originalAttempt,null);
        UUID cycle=store.user(1).cycleId();
        clock.advance(Duration.ofSeconds(9));
        assertThat(store.checkAccount(1).strikes()).isEqualTo(1);
        clock.advance(Duration.ofSeconds(1));
        assertThat(store.user(1).strikes()).isZero();
        assertThat(store.checkAccount(1).strikes()).isZero();
        assertThat(store.user(1).cycleId()).isEqualTo(cycle);
        assertThat(strike(1,originalAttempt,null).strikes()).isZero();
        assertThat(strike(1,UUID.randomUUID(),null).strikes()).isEqualTo(1);
        assertThat(store.strikes(1,0,25).total()).isEqualTo(2);
        assertThat(store.checkAccount(2).blocked()).isFalse();
    }
    @Test void thirdDailyStrikeBlocksForExactly24HoursAndSurvivesMidnight() {
        strike(1,UUID.randomUUID(),"a".repeat(64)); strike(1,UUID.randomUUID(),"b".repeat(64));
        Decision third=strike(1,UUID.randomUUID(),"c".repeat(64));
        assertThat(third.blockedUntil()).isEqualTo(clock.instant().plus(Duration.ofDays(1)));
        clock.advance(Duration.ofHours(20));
        assertThat(store.checkAccount(1).blocked()).isTrue();
        assertThat(store.checkAccount(1).strikes()).isEqualTo(3);
        assertThat(store.checkAccount(2).blocked()).isFalse();
    }
    @Test void attemptCrossingMidnightUsesOneConsistentObservationTime() {
        var shifting=org.mockito.Mockito.mock(Clock.class);
        Instant before=Instant.parse("2026-02-01T04:59:59Z"), after=before.plusSeconds(2);
        org.mockito.Mockito.when(shifting.instant()).thenReturn(before,after);
        var boundaryStore=new PostgresAccessControlRepository(JdbcClient.create(data),new DataSourceTransactionManager(data),shifting);
        assertThat(boundaryStore.recordUnauthorizedDevice(1L,null,UUID.randomUUID(),3,null,60).strikes()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT occurred_at FROM security_access_strike WHERE user_id=1",Timestamp.class).toInstant()).isEqualTo(before);
        assertThat(boundaryStore.recordUnauthorizedDevice(1L,null,UUID.randomUUID(),3,null,60).strikes()).isEqualTo(1);
        assertThat(boundaryStore.strikes(1L,0,25).total()).isEqualTo(2);
    }
    @Test void changingConfiguredDurationDoesNotRewriteExistingBlocks() {
        reachThreshold(); Instant previous=store.checkAccount(1).blockedUntil();
        store.setBlockDays(2,"admin","Nueva duracion autorizada");
        assertThat(store.checkAccount(1).blockedUntil()).isEqualTo(previous);
        store.unblock(1,"admin","Liberacion administrativa aprobada");
        strike(1,UUID.randomUUID(),"a".repeat(64));strike(1,UUID.randomUUID(),"b".repeat(64));
        var third=strike(1,UUID.randomUUID(),"c".repeat(64));
        assertThat(third.blockedUntil()).isEqualTo(clock.instant().plus(Duration.ofDays(2)));
    }
    @Test void repeatingDailyMigrationPreservesConfiguredDaysAndExistingBlocks() throws Exception {
        store.setBlockDays(2,"admin","Bloqueo de dos dias aprobado");
        reachThreshold(); var blocked=store.checkAccount(1);
        migrate("20261112_daily_access_strikes.sql"); migrate("20261112_daily_access_strikes.sql");
        assertThat(store.blockDays()).isEqualTo(2);
        assertThat(store.checkAccount(1)).isEqualTo(blocked);
        assertThat(store.strikes(1,0,25).total()).isEqualTo(3);
    }
    @Test void dailyMigrationRecountsTodaysLegacyStrikesButPreservesActiveBlockExpiration() throws Exception {
        UUID cycle=UUID.randomUUID(),blockedCycle=UUID.randomUUID();
        jdbc.update("INSERT INTO security_user_access_control(user_id,cycle_id,strike_count) VALUES(1,?,2)",cycle);
        jdbc.update("INSERT INTO security_user_access_control(user_id,cycle_id,strike_count,blocked_until) VALUES(2,?,3,CURRENT_TIMESTAMP+INTERVAL '5 days')",blockedCycle);
        Instant originalUntil=jdbc.queryForObject("SELECT blocked_until FROM security_user_access_control WHERE user_id=2",Timestamp.class).toInstant();
        for(String offset:List.of("0 days","1 day")) {
            jdbc.update("INSERT INTO security_access_strike(id,user_id,cycle_id,attempt_id,occurred_at,reason,threshold) VALUES(?,1,?,?,CURRENT_TIMESTAMP-CAST(? AS INTERVAL),'UNAUTHORIZED_DEVICE',3)",UUID.randomUUID(),cycle,UUID.randomUUID(),offset);
        }
        jdbc.execute("ALTER TABLE security_user_access_control DROP COLUMN strike_day; ALTER TABLE security_access_control_settings DROP COLUMN block_days");
        migrate("20261112_daily_access_strikes.sql");
        assertThat(jdbc.queryForObject("SELECT strike_count FROM security_user_access_control WHERE user_id=1",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT blocked_until FROM security_user_access_control WHERE user_id=2",Timestamp.class).toInstant()).isEqualTo(originalUntil);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_access_strike",Integer.class)).isEqualTo(2);
        assertThat(store.blockDays()).isEqualTo(1);
    }
    @Test void expiryAndAdministratorReleaseStartNewCyclesWithoutErasingHistoryOrReusingAttempts() {
        UUID old=UUID.randomUUID(); strike(1,old,"a".repeat(64));strike(1,UUID.randomUUID(),"b".repeat(64));
        var third=strike(1,UUID.randomUUID(),"c".repeat(64));
        clock.value=third.blockedUntil(); assertThat(store.checkAccount(1)).isEqualTo(new Decision(false,0,null));
        assertThat(strike(1,old,null).strikes()).isZero();
        strike(1,UUID.randomUUID(),null); store.unblock(1,"admin","Liberacion administrativa aprobada");
        assertThat(store.checkAccount(1).strikes()).isZero(); assertThat(store.strikes(1,0,25).total()).isEqualTo(4);
        assertThat(store.history(1,0,25).items()).extracting(Change::eventType).contains("BLOCK_EXPIRED","ACCOUNT_RELEASED");
    }
    @Test void concurrentUniqueAttemptsCannotLoseCountsOrExceedTheThresholdCycle() throws Exception {
        List<Callable<Decision>> attempts=new ArrayList<>();
        for(int i=0;i<8;i++) { String origin=Integer.toHexString(i).repeat(64); attempts.add(()->strike(1,UUID.randomUUID(),origin)); }
        concurrently(attempts);
        assertThat(store.checkAccount(1).strikes()).isEqualTo(3); assertThat(store.checkAccount(1).blocked()).isTrue();
        assertThat(store.strikes(1,0,25).total()).isEqualTo(3);
    }
    @Test void failedHistoryPersistenceRollsBackStrikeAndCounterAtomically() {
        strike(1,UUID.randomUUID(),"a".repeat(64)); strike(1,UUID.randomUUID(),"b".repeat(64));
        jdbc.execute("ALTER TABLE security_access_control_change ADD CONSTRAINT test_reject_change CHECK(FALSE)");
        assertThatThrownBy(()->strike(1,UUID.randomUUID(),"c".repeat(64))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(store.checkAccount(1).strikes()).isEqualTo(2); assertThat(store.strikes(1,0,25).total()).isEqualTo(2);
    }
    @Test void rejectingAnOuterRequestCannotRollBackAnAlreadyDecidedStrike() {
        var outer=new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(data));
        outer.executeWithoutResult(status->{strike(1,UUID.randomUUID(),null);status.setRollbackOnly();});
        assertThat(store.checkAccount(1).strikes()).isEqualTo(1);
        assertThat(store.strikes(1,0,25).total()).isEqualTo(1);
    }
    @Test void boundedViewsEscapeWildcardSearchAndDoNotExposeAttemptOrOriginKeys() throws Exception {
        strike(1,UUID.randomUUID(),"a".repeat(64));
        assertThat(store.users("%",0,25).items()).isEmpty(); assertThat(store.users("cliente_",0,1).items()).hasSize(1);
        String json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(store.strikes(1,0,25));
        assertThat(json).doesNotContain("attemptId","originKey","a".repeat(64));
    }
    private boolean bindOutcome(UUID pc) { try {store.bind(1,pc,"admin","Solicitud concurrente aprobada");return true;}catch(AccessControlException e){return false;} }
    private boolean rawBindOutcome(UUID pc) { try {jdbc.update("INSERT INTO security_user_device_binding(id,user_id,device_id) VALUES(?,1,?)",UUID.randomUUID(),pc);return true;}catch(org.springframework.dao.DataAccessException e){return false;} }
    private static <T> List<T> concurrently(List<Callable<T>> actions) throws Exception {
        CountDownLatch ready=new CountDownLatch(actions.size()),go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(actions.size())) {
            List<Future<T>> futures=new ArrayList<>();
            for(var action:actions) futures.add(pool.submit(()->{ready.countDown();go.await(10,TimeUnit.SECONDS);return action.call();}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();List<T> result=new ArrayList<>();for(var future:futures)result.add(future.get(15,TimeUnit.SECONDS));return result;
        }
    }
    private static final class MutableClock extends Clock {
        private volatile Instant value;
        private MutableClock(Instant value) { this.value=value; }
        void advance(Duration duration) { value=value.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return value; }
    }
}
