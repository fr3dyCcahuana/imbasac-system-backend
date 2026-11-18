package com.paulfernandosr.possystembackend.devicecertificate;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="CERTIFICATES_TEST_JDBC_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/certificates_test")
class CaRevocationOutboxPostgresIntegrationTest {
    private DriverManagerDataSource admin;private com.zaxxer.hikari.HikariDataSource data;private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;private CertificateSigner signer;private CaRevocationOutbox outbox;private String schema;
    private final UUID certificate=UUID.randomUUID(),device=UUID.randomUUID();
    private final String pem="-----BEGIN CERTIFICATE-----\n"+"A".repeat(80)+"\n-----END CERTIFICATE-----\n";
    private final MutableClock clock=new MutableClock(Instant.now().plusSeconds(10));
    @BeforeEach void setup() throws Exception {
        String url=System.getenv("CERTIFICATES_TEST_JDBC_URL");admin=new DriverManagerDataSource(url,"postgres",System.getenv("CERTIFICATES_TEST_PASSWORD"));
        schema="ca_outbox_test_"+UUID.randomUUID().toString().replace("-","");new JdbcTemplate(admin).execute("CREATE SCHEMA "+schema);
        data=new com.zaxxer.hikari.HikariDataSource();data.setJdbcUrl(url+"?currentSchema="+schema);data.setUsername("postgres");data.setPassword(System.getenv("CERTIFICATES_TEST_PASSWORD"));data.setMaximumPoolSize(6);data.setMinimumIdle(0);
        jdbc=new JdbcTemplate(data);transactions=new DataSourceTransactionManager(data);
        jdbc.execute("CREATE TABLE security_device(id UUID PRIMARY KEY);CREATE TABLE security_device_certificate(id UUID PRIMARY KEY)");
        jdbc.update("INSERT INTO security_device VALUES(?)",device);jdbc.update("INSERT INTO security_device_certificate VALUES(?)",certificate);
        migrate();signer=mock(CertificateSigner.class);when(signer.isConfigured()).thenReturn(true);outbox=new CaRevocationOutbox(JdbcClient.create(data),transactions,signer,true,clock);
    }
    private void migrate() throws Exception {jdbc.execute(new String(new ClassPathResource("db/scripts/20261030_ca_revocation_outbox.sql").getInputStream().readAllBytes(),StandardCharsets.UTF_8));}
    @AfterEach void cleanup(){if(outbox!=null)outbox.close();if(data!=null)data.close();if(schema!=null&&schema.matches("ca_outbox_test_[a-f0-9]{32}"))new JdbcTemplate(admin).execute("DROP SCHEMA "+schema+" CASCADE");}
    private void enqueue(){outbox.enqueue(certificate,device,pem);}
    private String state(){return jdbc.queryForObject("SELECT status FROM security_ca_revocation_outbox WHERE certificate_id=?",String.class,certificate);}
    @Test void enqueueAndMigrationAreRepeatableAndDoNotContactCaBeforeTheWorker() throws Exception {
        enqueue();enqueue();migrate();assertThat(outbox.pendingCount()).isEqualTo(1);verify(signer,never()).revoke(any(),any(),anyString());
    }
    @Test void successfulDeliveryCompletesOnlyAfterConfirmedSignerReturn() {
        enqueue();outbox.runOnce();assertThat(state()).isEqualTo("COMPLETED");assertThat(outbox.pendingCount()).isZero();
        verify(signer).revoke(certificate,device,pem);outbox.runOnce();verify(signer,times(1)).revoke(certificate,device,pem);
        assertThat(jdbc.queryForObject("SELECT completed_at IS NOT NULL AND leased_until IS NULL AND lease_token IS NULL FROM security_ca_revocation_outbox",Boolean.class)).isTrue();
    }
    @Test void sixFailuresUseBoundedBackoffAndRequireReviewWithoutFakingCompletion() {
        enqueue();doThrow(new IllegalStateException("SECRET_INTERNAL_HELPER_PATH")).when(signer).revoke(any(),any(),anyString());
        for(int attempt=1;attempt<=6;attempt++) {
            outbox.runOnce();assertThat(jdbc.queryForObject("SELECT attempts FROM security_ca_revocation_outbox",Integer.class)).isEqualTo(attempt);
            outbox.runOnce();verify(signer,times(attempt)).revoke(any(),any(),anyString());
            long seconds=jdbc.queryForObject("SELECT EXTRACT(EPOCH FROM(next_attempt_at-?))::BIGINT FROM security_ca_revocation_outbox",Long.class,java.sql.Timestamp.from(clock.instant()));
            assertThat(seconds).isBetween(60L,3600L);clock.advance(3601);
        }
        assertThat(state()).isEqualTo("NEEDS_REVIEW");assertThat(outbox.needsReviewCount()).isEqualTo(1);assertThat(outbox.pendingCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_error_code FROM security_ca_revocation_outbox",String.class)).isEqualTo("SIGNER_REVOKE_FAILED");
        assertThat(jdbc.queryForObject("SELECT completed_at IS NULL FROM security_ca_revocation_outbox",Boolean.class)).isTrue();
        outbox.runOnce();verify(signer,times(6)).revoke(any(),any(),anyString());
    }
    @Test void anExpiredLeaseIsRecoveredByAnIdempotentRevocation() {
        enqueue();jdbc.update("UPDATE security_ca_revocation_outbox SET attempts=1,leased_until=?,lease_token=?",java.sql.Timestamp.from(clock.instant().minusSeconds(1)),UUID.randomUUID());
        outbox.runOnce();assertThat(state()).isEqualTo("COMPLETED");assertThat(jdbc.queryForObject("SELECT attempts FROM security_ca_revocation_outbox",Integer.class)).isEqualTo(2);
    }
    @Test void exhaustedCrashedLeaseRequiresOperatorReviewInsteadOfAnotherExternalCall() {
        enqueue();jdbc.update("UPDATE security_ca_revocation_outbox SET attempts=6,leased_until=?,lease_token=?",java.sql.Timestamp.from(clock.instant().minusSeconds(1)),UUID.randomUUID());
        outbox.runOnce();assertThat(state()).isEqualTo("NEEDS_REVIEW");assertThat(outbox.needsReviewCount()).isEqualTo(1);
        verify(signer,never()).revoke(any(),any(),anyString());
        assertThat(jdbc.queryForObject("SELECT last_error_code FROM security_ca_revocation_outbox",String.class)).isEqualTo("DELIVERY_OUTCOME_UNCONFIRMED");
    }
    @Test void aLateFailedWorkerCannotOverwriteANewerSuccessfulLease() throws Exception {
        enqueue();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
        doAnswer(invocation->{if(calls.incrementAndGet()==1){entered.countDown();release.await(10,TimeUnit.SECONDS);throw new IllegalStateException("late worker failure");}return null;}).when(signer).revoke(any(),any(),anyString());
        try(var pool=Executors.newSingleThreadExecutor()) {
            Future<?> first=pool.submit(outbox::runOnce);assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();clock.advance(301);
            outbox.runOnce();assertThat(state()).isEqualTo("COMPLETED");release.countDown();first.get(10,TimeUnit.SECONDS);
        } finally {release.countDown();}
        assertThat(state()).isEqualTo("COMPLETED");assertThat(jdbc.queryForObject("SELECT last_error_code IS NULL FROM security_ca_revocation_outbox",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT attempts FROM security_ca_revocation_outbox",Integer.class)).isEqualTo(2);
    }
    @Test void twoWorkersDoNotDeliverTheSameUnexpiredLease() throws Exception {
        enqueue();CountDownLatch start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{start.await();outbox.runOnce();return null;});var second=pool.submit(()->{start.await();outbox.runOnce();return null;});
            start.countDown();first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
        }
        assertThat(state()).isEqualTo("COMPLETED");verify(signer,times(1)).revoke(certificate,device,pem);
    }
    @Test void queuedDeliveryRollsBackTogetherWithTheCallersRevocationTransaction() {
        new TransactionTemplate(transactions).executeWithoutResult(status->{enqueue();status.setRollbackOnly();});
        assertThat(outbox.pendingCount()).isZero();
    }
    @Test void missingPublicCertificateStaysInReviewAndCannotEnterTheWorker() {
        jdbc.update("INSERT INTO security_ca_revocation_outbox(certificate_id,device_id,status,last_error_code) VALUES(?,?,'NEEDS_REVIEW','CERTIFICATE_PEM_UNAVAILABLE')",certificate,device);
        outbox.runOnce();assertThat(state()).isEqualTo("NEEDS_REVIEW");assertThat(outbox.pendingCount()).isEqualTo(1);verify(signer,never()).revoke(any(),any(),anyString());
    }
    @Test void databaseOutageDoesNotThrowIntoTheSchedulerOrCallAnExternalSigner() {
        jdbc.execute("DROP TABLE security_ca_revocation_outbox");assertThatCode(outbox::runOnce).doesNotThrowAnyException();verify(signer,never()).revoke(any(),any(),anyString());
    }
    @Test void scheduledTickDoesNotBlockOtherScheduledJobsOrEnqueueRepeatedWork() throws Exception {
        enqueue();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        doAnswer(invocation->{entered.countDown();release.await(10,TimeUnit.SECONDS);return null;}).when(signer).revoke(any(),any(),anyString());
        try {
            outbox.scheduledTick();assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            assertThatCode(outbox::scheduledTick).doesNotThrowAnyException();
            verify(signer,times(1)).revoke(certificate,device,pem);
        } finally {release.countDown();}
        for(int retry=0;retry<100 && !"COMPLETED".equals(state());retry++) Thread.sleep(10);
        assertThat(state()).isEqualTo("COMPLETED");
    }
    private static final class MutableClock extends Clock {
        private volatile Instant value;MutableClock(Instant value){this.value=value;}void advance(long seconds){value=value.plusSeconds(seconds);}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}@Override public Clock withZone(ZoneId zone){return this;}@Override public Instant instant(){return value;}
    }
}
