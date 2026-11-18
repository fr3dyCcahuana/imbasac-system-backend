package com.paulfernandosr.possystembackend.accessaudit.application;

import com.paulfernandosr.possystembackend.accessaudit.domain.*;
import com.paulfernandosr.possystembackend.accessaudit.domain.AccessAuditModels.*;
import com.paulfernandosr.possystembackend.accessaudit.domain.port.AccessAuditRepository;
import com.paulfernandosr.possystembackend.accessaudit.domain.port.AuditMetadataProvider;
import com.paulfernandosr.possystembackend.accessaudit.domain.port.AuditSessionInvalidator;
import com.paulfernandosr.possystembackend.deviceaccess.domain.DevicePolicyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class AccessAuditService {
    private final AccessAuditRepository repository;
    private final AuditMetadataProvider metadata;
    private final boolean enabled;
    private final AuditSessionInvalidator invalidator;
    private final com.paulfernandosr.possystembackend.trustedclient.TrustedClientResolver clients;
    private final com.paulfernandosr.possystembackend.devicecertificate.DeviceCertificateService certificates;
    @Value("${security.access-audit.denial-details-per-minute:100}")
    private int denialDetailsPerMinute=100;
    @Value("${security.access-audit.failure-alert-threshold:5}")
    private int failureAlertThreshold=5;
    private long denialMinute=-1;
    private int denialDetails;
    private final java.util.concurrent.atomic.AtomicLong nextWarning=new java.util.concurrent.atomic.AtomicLong();
    public AccessAuditService(AccessAuditRepository repository, AuditMetadataProvider metadata,
            boolean enabled, AuditSessionInvalidator invalidator) { this(repository,metadata,enabled,invalidator,null,null); }
    @org.springframework.beans.factory.annotation.Autowired
    public AccessAuditService(AccessAuditRepository repository, AuditMetadataProvider metadata,
            @Value("${security.access-audit.enabled:false}") boolean enabled, AuditSessionInvalidator invalidator,
            com.paulfernandosr.possystembackend.trustedclient.TrustedClientResolver clients,
            com.paulfernandosr.possystembackend.devicecertificate.DeviceCertificateService certificates) {
        this.repository=repository; this.metadata=metadata; this.enabled=enabled; this.invalidator=invalidator;
        this.clients=clients;this.certificates=certificates;
    }
    public boolean isEnabled() { return enabled; }
    public Status status() {
        int threshold=Math.max(1,Math.min(100000,failureAlertThreshold));
        boolean ready=certificates!=null && certificates.isReady();
        String source=clients!=null && clients.isEnabled()?"AUTHENTICATED_MTLS_PROXY":"BACKEND_CONNECTION";
        if (!enabled) return new Status(false,"America/Lima",null,31,5000,ready,source,threshold);
        return new Status(true,"America/Lima",repository.firstEvent(),31,5000,ready,source,threshold,repository.authenticationContextReady());
    }
    @Transactional(readOnly=true,timeout=5)
    public Summary summary(AuditQuery query) { requireEnabled(); return repository.summary(query); }
    @Transactional(readOnly=true,timeout=5)
    public Page<Event> events(AuditQuery query) {
        requireEnabled(); return repository.events(query,query.size(),query.page()*query.size());
    }
    @Transactional(readOnly=true,timeout=5)
    public Event event(UUID id) {
        requireEnabled(); return repository.event(id).orElseThrow(()->new AuditException(404,"ACCESS_AUDIT_NOT_FOUND","El evento no existe."));
    }
    @Transactional(readOnly=true,timeout=5)
    public Page<SessionRow> sessions(AuditQuery query, String state) {
        requireEnabled();
        if (state!=null && !Set.of("ACTIVE","LOGGED_OUT","REPLACED","REVOKED","INVALIDATED","EXPIRED").contains(state)) {
            throw AuditException.badRequest("El estado de sesión no es válido.");
        }
        return repository.sessions(query,state);
    }
    @Transactional(timeout=5)
    public void revoke(UUID id, String actor, String reason) {
        requireEnabled();
        String clean=reason==null ? "" : reason.replaceAll("[\\p{Cntrl}]"," ").strip();
        if (clean.length()<10 || reason.length()>300) {
            throw AuditException.badRequest("Indica un motivo de entre 10 y 300 caracteres.");
        }
        if (repository.revoke(id,actor,clean,metadata.current())) {
            repository.sessionReference(id).ifPresent(reference->invalidator.invalidate(reference.key(),reference.username()));
        }
    }
    @Transactional(timeout=5)
    public byte[] export(AuditQuery query, String actor) {
        requireEnabled();
        Page<Event> page=repository.events(query,5001,0);
        if (page.total()>5000 || page.items().size()>5000) throw new AuditException(409,"ACCESS_AUDIT_EXPORT_LIMIT","Reduce el período o los filtros: la exportación admite hasta 5000 filas.");
        StringBuilder csv=new StringBuilder("\uFEFFFecha Lima;Última repetición Lima;Evento;Resultado;Fuente;Usuario identificado;Cuenta intentada;Rol;Actor;Motivo;IP observada;Navegador declarado;Repeticiones;Correlación;Categoría;Estado del certificado;Computadora;Identificador PC;Serie pública;Huella pública SHA-256;Comprobación del rechazo\r\n");
        for (Event e:page.items()) {
            var context=e.authentication();
            String[] values={lima(e.occurredAt()),lima(e.lastOccurredAt()),e.eventType(),e.outcome(),e.source(),e.username(),e.attemptedUsername(),e.roleName(),e.actorUsername(),e.reason(),e.peerAddress(),e.userAgent(),Long.toString(e.occurrences()),e.correlationId().toString(),
                    AuditCategory.label(e.category()),context==null?null:context.certificateState(),context==null?null:context.deviceName(),
                    context==null || context.deviceId()==null?null:context.deviceId().toString(),context==null?null:context.serialHex(),context==null?null:context.fingerprintSha256(),context==null?null:context.failureReason()};
            csv.append(String.join(";",Arrays.stream(values).map(AccessAuditService::csvCell).toList())).append("\r\n");
        }
        repository.record("AUDIT_EXPORTED","INFO","BACKEND",actor,null,null,actor,
                "CSV_PREPARED_ROWS_"+page.items().size(),metadata.current(),false);
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    public void loginRejected(String attempted, RuntimeException error) {
        String reason=error instanceof DevicePolicyException policy ? policy.getCode().name()
                : error instanceof com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException control ? control.getCode().name()
                : (error instanceof org.springframework.security.core.AuthenticationException
                || error instanceof com.paulfernandosr.possystembackend.security.domain.exception.InvalidCredentialsException
                || error instanceof com.paulfernandosr.possystembackend.user.domain.exception.UserNotFoundException)
                ? "CREDENTIALS_OR_ACCOUNT_REJECTED" : "AUTHENTICATION_UNAVAILABLE";
        var meta=metadata.current();
        // Preserve a real account block as the main decision; its originating failure stays in the snapshot.
        boolean deviceDenial=error instanceof DevicePolicyException policy
                && (policy.getCode()==DevicePolicyException.Code.DEVICE_NOT_AUTHORIZED || policy.getCode()==DevicePolicyException.Code.DEVICE_CERTIFICATE_INVALID)
                || error instanceof com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException control
                && control.getCode()==com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException.Code.ACCESS_DEVICE_NOT_AUTHORIZED;
        if(deviceDenial && meta.authentication()!=null && meta.authentication().failureReason()!=null)
            reason=meta.authentication().failureReason();
        safeRecord("LOGIN_REJECTED","REJECTED","BACKEND",null,attempted,null,null,reason,meta,true);
    }
    public void viewed(String actor) {
        viewed(actor,metadata.current());
    }
    public void viewed(String actor, Metadata metadata) {
        safeRecord("AUDIT_VIEWED","INFO","BACKEND",actor,null,null,actor,"ACCESS_DASHBOARD_VIEWED",metadata,true);
    }
    public void denied(String type, String username, Metadata metadata) {
        denied(type,username,null,null,metadata);
    }
    public void denied(String type,String username,String key,String failure,Metadata metadata) {
        String reason=failure!=null?failure:switch(type) {
                    case "ACCESS_DENIED" -> "PERMISSION_OR_POLICY_DENIED";
                    case "LOGIN_REJECTED" -> "LOGIN_REQUEST_NOT_ACCEPTED";
                    default -> "SESSION_NOT_ACCEPTED";
                };
        if(enabled && "SESSION_NOT_ACTIVE".equals(reason) && key!=null) {
            try { reason=repository.sessionRejectionReason(key).orElse(reason); }
            catch(RuntimeException ignored) { /* Diagnosis cannot change the rejected response. */ }
        }
        safeRecord(type,"REJECTED","BACKEND",username,null,key,null,reason,metadata,true);
    }
    public void browser(String username, String key, boolean completed) {
        safeRecord(completed ? "BROWSER_COMPLETED" : "BROWSER_REJECTED",completed ? "INFO" : "REJECTED",
                "BROWSER",username,null,key,null,"CLIENT_DECLARED_VALIDATION",metadata.current(),true);
    }
    private void safeRecord(String type,String outcome,String source,String user,String attempted,String key,String actor,String reason,Metadata meta,boolean aggregate) {
        if (!enabled) return;
        if ("REJECTED".equals(outcome) && !allowDenialDetail()) {
            user=null; attempted=null; key=null; actor=null; reason="HIGH_VOLUME_DENIAL_SUMMARY";
            meta=new Metadata(null,null,meta.correlationId()); aggregate=true;
        }
        try { repository.record(type,outcome,source,user,attempted,key,actor,reason,meta,aggregate); }
        catch (RuntimeException e) {
            long now=System.currentTimeMillis()/1000, previous=nextWarning.get();
            if (now>=previous && nextWarning.compareAndSet(previous,now+60)) {
                log.warn("Access audit capture unavailable ({}); event data and exception details omitted",e.getClass().getSimpleName());
            }
        }
    }
    private synchronized boolean allowDenialDetail() {
        long minute=System.currentTimeMillis()/60000;
        if (minute!=denialMinute) { denialMinute=minute; denialDetails=0; }
        return ++denialDetails<=Math.max(1,Math.min(1000,denialDetailsPerMinute));
    }
    private void requireEnabled() { if (!enabled) throw AuditException.disabled(); }
    private static String lima(Instant time) {
        return DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(AuditQuery.LIMA).format(time);
    }
    public static String csvCell(String input) {
        String value=input==null ? "" : input.replaceAll("[\\p{Cntrl}]"," ");
        String stripped=value.stripLeading();
        if (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0))>=0) value="'"+value;
        return "\""+value.replace("\"","\"\"")+"\"";
    }
}
