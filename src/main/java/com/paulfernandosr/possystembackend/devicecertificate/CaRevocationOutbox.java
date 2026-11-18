package com.paulfernandosr.possystembackend.devicecertificate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PreDestroy;

/** Asynchronous CA delivery. No request, sale or authentication transaction waits for the signer. */
@Component
public class CaRevocationOutbox {
    private static final Logger log=LoggerFactory.getLogger(CaRevocationOutbox.class);
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final CertificateSigner signer;
    private final boolean enabled;
    private final Clock clock;
    private final AtomicBoolean running=new AtomicBoolean();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(task,"ca-revocation-delivery");thread.setDaemon(true);return thread;});
    @Autowired public CaRevocationOutbox(JdbcClient jdbc,PlatformTransactionManager manager,CertificateSigner signer,
            @Value("${security.ca-signer.revocation-delivery-enabled:${security.ca-signer.enabled:false}}") boolean enabled) {
        this(jdbc,manager,signer,enabled,Clock.systemUTC());
    }
    public CaRevocationOutbox(JdbcClient jdbc,PlatformTransactionManager manager,CertificateSigner signer,boolean enabled,Clock clock) {
        this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.signer=signer;this.enabled=enabled;this.clock=clock;
    }
    /** Caller may join this insert to its certificate-revocation transaction. Does not call the CA. */
    public void enqueue(UUID certificateId,UUID deviceId,String publicCertificatePem) {
        if(certificateId==null||deviceId==null||publicCertificatePem==null||publicCertificatePem.length()>16000||publicCertificatePem.length()<50||
                !publicCertificatePem.startsWith("-----BEGIN CERTIFICATE-----")||publicCertificatePem.contains("PRIVATE KEY"))
            throw new IllegalArgumentException("Invalid public certificate revocation delivery");
        jdbc.sql("""
                INSERT INTO security_ca_revocation_outbox(certificate_id,device_id,certificate_pem)
                VALUES(:id,:device,:pem) ON CONFLICT(certificate_id) DO NOTHING
                """).param("id",certificateId).param("device",deviceId).param("pem",publicCertificatePem).update();
    }
    /** Includes operator-review jobs: completed delivery is the only resolved status. */
    public long pendingCount() { return jdbc.sql("SELECT COUNT(*) FROM security_ca_revocation_outbox WHERE status<>'COMPLETED'").query(Long.class).single(); }
    public long needsReviewCount() { return jdbc.sql("SELECT COUNT(*) FROM security_ca_revocation_outbox WHERE status='NEEDS_REVIEW'").query(Long.class).single(); }
    @Scheduled(fixedDelay=60000)
    public void scheduledTick() {
        if(!enabled||!running.compareAndSet(false,true)) return;
        try {worker.execute(()->{try {runOnce();}finally {running.set(false);}});}
        catch(RejectedExecutionException stopped) {running.set(false);}
    }
    @PreDestroy public void close() {worker.shutdownNow();}
    // Kept synchronous for operator-triggered checks and deterministic integration tests.
    public void runOnce() {
        if(!enabled) return;
        try {
            if(!signer.isConfigured()) return;
            Job job=claim();
            if(job==null||!"PENDING".equals(job.status())) return;
            try {
                // The lease transaction has committed. External I/O is idempotent by certificate identity.
                signer.revoke(job.certificateId(),job.deviceId(),job.pem());
            } catch(RuntimeException unavailable) { failed(job);return; }
            jdbc.sql("""
                    UPDATE security_ca_revocation_outbox SET status='COMPLETED',completed_at=:now,leased_until=NULL,
                    lease_token=NULL,last_error_code=NULL WHERE certificate_id=:id AND lease_token=:token AND status='PENDING'
                    """).param("now",Timestamp.from(clock.instant())).param("id",job.certificateId()).param("token",job.token()).update();
        } catch(RuntimeException unavailable) {
            // Never log signer output, PEM, credentials, private paths or exception messages.
            log.warn("CA revocation delivery unavailable; durable pending state retained");
        }
    }
    private Job claim() {
        return tx.execute(status->jdbc.sql("""
                WITH candidate AS (
                  SELECT certificate_id FROM security_ca_revocation_outbox
                  WHERE status='PENDING' AND next_attempt_at<=:now AND (leased_until IS NULL OR leased_until<=:now)
                  ORDER BY next_attempt_at,created_at,certificate_id FOR UPDATE SKIP LOCKED LIMIT 1
                )
                UPDATE security_ca_revocation_outbox o SET
                  status=CASE WHEN o.attempts>=6 THEN 'NEEDS_REVIEW' ELSE 'PENDING' END,
                  attempts=CASE WHEN o.attempts>=6 THEN o.attempts ELSE o.attempts+1 END,
                  leased_until=CASE WHEN o.attempts>=6 THEN NULL ELSE CAST(:until AS TIMESTAMPTZ) END,
                  lease_token=CASE WHEN o.attempts>=6 THEN NULL ELSE CAST(:token AS UUID) END,
                  last_error_code=CASE WHEN o.attempts>=6 THEN 'DELIVERY_OUTCOME_UNCONFIRMED' ELSE o.last_error_code END
                FROM candidate c WHERE o.certificate_id=c.certificate_id
                RETURNING o.certificate_id,o.device_id,o.certificate_pem,o.attempts,o.lease_token,o.status
                """).param("now",Timestamp.from(clock.instant())).param("until",Timestamp.from(clock.instant().plusSeconds(300)))
                .param("token",UUID.randomUUID()).query((rs,n)->new Job(rs.getObject("certificate_id",UUID.class),rs.getObject("device_id",UUID.class),
                        rs.getString("certificate_pem"),rs.getInt("attempts"),rs.getObject("lease_token",UUID.class),rs.getString("status")))
                .optional().orElse(null));
    }
    private void failed(Job job) {
        long seconds=Math.min(3600,60L<<(job.attempts()-1));
        jdbc.sql("""
                UPDATE security_ca_revocation_outbox SET status=:status,next_attempt_at=:next,leased_until=NULL,lease_token=NULL,
                last_error_code='SIGNER_REVOKE_FAILED' WHERE certificate_id=:id AND lease_token=:token AND status='PENDING'
                """).param("status",job.attempts()>=6?"NEEDS_REVIEW":"PENDING").param("next",Timestamp.from(clock.instant().plusSeconds(seconds)))
                .param("id",job.certificateId()).param("token",job.token()).update();
    }
    private record Job(UUID certificateId,UUID deviceId,String pem,int attempts,UUID token,String status) {
        @Override public String toString() { return "CaRevocationJob[redacted]"; }
    }
}
