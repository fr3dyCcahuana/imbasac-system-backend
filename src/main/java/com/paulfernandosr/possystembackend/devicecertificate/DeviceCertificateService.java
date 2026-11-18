package com.paulfernandosr.possystembackend.devicecertificate;

import com.paulfernandosr.possystembackend.devicecertificate.CertificateModels.*;
import com.paulfernandosr.possystembackend.trustedclient.*;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.openssl.PEMParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.time.Instant;
import java.util.*;
import static com.paulfernandosr.possystembackend.devicecertificate.CertificateException.Code.*;

/** Stores public evidence only. Signing and private CA keys remain outside the ERP process. */
@Service
public class DeviceCertificateService {
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final TrustedClientResolver transport;
    private final GeoLocationService geo;
    private final boolean enabled;
    private final boolean registrationEnabled;
    private final boolean enrollmentEnabled;
    private final List<X509Certificate> issuers;
    private final CertificateSigner signer;
    public DeviceCertificateService(JdbcClient jdbc,PlatformTransactionManager manager,TrustedClientResolver transport,
            GeoLocationService geo,boolean enabled,String issuerPath) { this(jdbc,manager,transport,geo,enabled,issuerPath,null,enabled); }
    public DeviceCertificateService(JdbcClient jdbc,PlatformTransactionManager manager,TrustedClientResolver transport,
            GeoLocationService geo,boolean enabled,String issuerPath,CertificateSigner signer) {
        this(jdbc,manager,transport,geo,enabled,issuerPath,signer,enabled);
    }
    public DeviceCertificateService(JdbcClient jdbc,PlatformTransactionManager manager,TrustedClientResolver transport,
            GeoLocationService geo,boolean enabled,String issuerPath,CertificateSigner signer,boolean registrationEnabled) {
        this(jdbc,manager,transport,geo,enabled,issuerPath,signer,registrationEnabled,false);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public DeviceCertificateService(JdbcClient jdbc,PlatformTransactionManager manager,TrustedClientResolver transport,
            GeoLocationService geo,@Value("${security.device-certificates.enabled:false}") boolean enabled,
            @Value("${security.device-certificates.issuer-bundle-path:}") String issuerPath,CertificateSigner signer,
            @Value("${security.access-control.enabled:false}") boolean registrationEnabled,
            @Value("${security.device-certificates.enrollment-enabled:false}") boolean enrollmentEnabled) {
        this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.transport=transport;this.geo=geo;this.enabled=enabled;
        this.registrationEnabled=registrationEnabled;
        this.enrollmentEnabled=enabled || (registrationEnabled && enrollmentEnabled);
        this.signer=signer;
        this.issuers=enabled ? loadIssuers(issuerPath) : List.of();
    }
    private static List<X509Certificate> loadIssuers(String issuerPath) {
        if(issuerPath==null || issuerPath.isBlank()) {
            throw new IllegalArgumentException("Device certificate issuer bundle path is not configured");
        }
        final Path path;
        try {
            path=Path.of(issuerPath);
        } catch(InvalidPathException failure) {
            throw issuerBundleFailure("path is invalid",failure);
        }
        try(InputStream input=Files.newInputStream(path)) {
            var certificates=CertificateFactory.getInstance("X.509").generateCertificates(input).stream().map(X509Certificate.class::cast).toList();
            if(certificates.isEmpty()) throw new IllegalArgumentException("Device certificate issuer bundle contains no X.509 certificates");
            return certificates;
        } catch(IOException | SecurityException failure) {
            throw issuerBundleFailure("cannot be read",failure);
        } catch(java.security.cert.CertificateException | ClassCastException failure) {
            throw issuerBundleFailure("cannot be parsed as X.509",failure);
        }
    }
    private static IllegalArgumentException issuerBundleFailure(String detail,Exception failure) {
        // Exception text and causes can contain the configured path or certificate input.
        return new IllegalArgumentException("Device certificate issuer bundle "+detail+" ("+failure.getClass().getSimpleName()+")");
    }
    public boolean isReady() { return enabled && transport.isEnabled() && !issuers.isEmpty(); }
    public Status status() {
        long[] counts=enabled?safe(()->jdbc.sql("SELECT COUNT(*) FILTER(WHERE status='PENDING'),COUNT(*) FILTER(WHERE status='NEEDS_REVIEW') FROM security_ca_revocation_outbox")
                .query((rs,n)->new long[]{rs.getLong(1),rs.getLong(2)}).single()):new long[]{0,0};
        return new Status(enabled,transport.isEnabled(),geo.isEnabled(),true,signer!=null && signer.isConfigured(),counts[0],counts[1],registrationEnabled,enrollmentEnabled);
    }
    public com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlModels.DeviceRow createDevice(NewDevice input,String actor) {
        if(!registrationEnabled) fail(DEVICE_REGISTRATION_DISABLED);
        String reason=reason(input.reason());
        if(input.displayName()==null || input.displayName().strip().isEmpty() || input.displayName().length()>120 || input.platform()==null
                || !Set.of("WINDOWS","MACOS").contains(input.platform()) || input.displayName().matches("(?s).*[\\p{Cntrl}].*")) fail(CERTIFICATE_REQUEST_INVALID);
        UUID id=UUID.randomUUID();Instant created=Instant.now();
        safe(()->tx.execute(status->{jdbc.sql("INSERT INTO security_device(id,display_name,platform,status,created_at) VALUES(:id,:name,:platform,'PENDING',:created)")
                .param("id",id).param("name",input.displayName().strip()).param("platform",input.platform()).param("created",java.sql.Timestamp.from(created)).update();
            change(id,actor,"DEVICE_REGISTERED",reason);return null;}));
        return new com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlModels.DeviceRow(id,input.displayName().strip(),input.platform(),"PENDING",created);
    }
    public UUID presentedDevice() {
        TrustedClientContext context=transport.current();
        if(!isReady()) throw diagnostic("DEVICE_CERTIFICATE_CHANNEL_NOT_READY",CERTIFICATE_DEVICE_NOT_AUTHORIZED);
        if(!context.proxyAuthenticated()) throw diagnostic("CERTIFICATE_TRANSPORT_UNTRUSTED",CERTIFICATE_DEVICE_NOT_AUTHORIZED);
        if("NONE".equals(context.certificateState())) throw diagnostic("CERTIFICATE_MISSING",CERTIFICATE_DEVICE_NOT_AUTHORIZED);
        if(!"SUCCESS".equals(context.certificateState()) || context.certificate()==null)
            throw diagnostic("CERTIFICATE_TRANSPORT_INVALID",CERTIFICATE_DEVICE_NOT_AUTHORIZED);
        X509Certificate certificate=context.certificate();
        UUID device=validateCertificate(certificate);
        try {
            String failure=jdbc.sql("""
                    SELECT CASE
                      WHEN c.device_id<>:device OR c.kind<>'DEVICE' THEN 'CERTIFICATE_INVALID'
                      WHEN c.revoked_at IS NOT NULL THEN 'CERTIFICATE_REVOKED'
                      WHEN c.not_before>CURRENT_TIMESTAMP THEN 'CERTIFICATE_NOT_YET_VALID'
                      WHEN c.expires_at<=CURRENT_TIMESTAMP THEN 'CERTIFICATE_EXPIRED'
                      WHEN d.status<>'ACTIVE' THEN 'CERTIFICATE_PC_INACTIVE'
                      WHEN d.hardware_approved IS NOT TRUE THEN 'CERTIFICATE_PC_NOT_APPROVED'
                      ELSE 'ACCEPTED' END
                    FROM security_device_certificate c JOIN security_device d ON d.id=c.device_id
                    WHERE c.fingerprint_sha256=:fp
                    """).param("fp",TrustedClientResolver.fingerprint(certificate)).param("device",device)
                    .query(String.class).optional().orElse("CERTIFICATE_NOT_REGISTERED");
            if(!"ACCEPTED".equals(failure)) throw diagnostic(failure,CERTIFICATE_DEVICE_NOT_AUTHORIZED);
            return device;
        } catch(DataAccessException ignored) { throw new CertificateException(CERTIFICATE_UNAVAILABLE); }
    }
    private static CertificateException diagnostic(String reason,CertificateException.Code code) {
        com.paulfernandosr.possystembackend.accessaudit.domain.AuditFailure.note(reason);
        return new CertificateException(code);
    }
    /** Expired/revoked own certificates are technical/revocation denials, not evidence of a different PC. */
    public boolean isRegisteredForUser(long userId,X509Certificate certificate) {
        if(certificate==null) return false;
        try { return jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM security_device_certificate c JOIN security_user_device_binding b ON b.device_id=c.device_id
                WHERE c.fingerprint_sha256=:fp AND b.user_id=:user AND b.revoked_at IS NULL)
                """).param("fp",TrustedClientResolver.fingerprint(certificate)).param("user",userId).query(Boolean.class).single(); }
        catch(DataAccessException ignored) { throw new CertificateException(CERTIFICATE_UNAVAILABLE); }
    }
    public List<CertificateRow> certificates(UUID deviceId) {
        requireEnabled(); requireDeviceId(deviceId);
        return safe(()->jdbc.sql("""
                SELECT c.*,d.hardware_approved,d.hardware_evidence_reference FROM security_device_certificate c
                JOIN security_device d ON d.id=c.device_id WHERE c.device_id=:id ORDER BY c.created_at DESC LIMIT 100
                """).param("id",deviceId).query((rs,n)->new CertificateRow(rs.getObject("id",UUID.class),rs.getObject("device_id",UUID.class),
                rs.getString("fingerprint_sha256"),rs.getString("serial_hex"),rs.getTimestamp("not_before").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),rs.getTimestamp("revoked_at")==null?null:rs.getTimestamp("revoked_at").toInstant(),
                rs.getBoolean("hardware_approved"),rs.getString("hardware_evidence_reference"))).list());
    }
    public List<Enrollment> enrollments(UUID deviceId) {
        requireEnrollmentEnabled();requireDeviceId(deviceId);
        return safe(()->jdbc.sql("SELECT * FROM security_device_enrollment WHERE device_id=:id ORDER BY created_at DESC LIMIT 100")
                .param("id",deviceId).query((rs,n)->new Enrollment(rs.getObject("id",UUID.class),deviceId,rs.getTimestamp("created_at").toInstant(),
                        rs.getString("status"),rs.getString("public_key_sha256"),rs.getString("csr_pem"))).list());
    }
    public Enrollment enroll(RegisterEnrollment input,String actor) {
        requireEnrollmentEnabled(); requireDeviceId(input.deviceId());
        String pem=input.csrPem();
        if(pem==null || pem.length()>16000 || !pem.matches("(?s)-----BEGIN (CERTIFICATE REQUEST|NEW CERTIFICATE REQUEST)-----\\r?\\n[A-Za-z0-9+/=\\r\\n]+-----END \\1-----[\\r\\n]*")) fail(CERTIFICATE_REQUEST_INVALID);
        String keyHash;
        try(PEMParser parser=new PEMParser(new StringReader(pem))) {
            Object parsed=parser.readObject();
            if(!(parsed instanceof PKCS10CertificationRequest csr) || parser.readObject()!=null) throw new IllegalArgumentException();
            PublicKey key=new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter().getPublicKey(csr.getSubjectPublicKeyInfo());
            strongKey(key);
            if(!csr.isSignatureValid(new JcaContentVerifierProviderBuilder().build(key))) throw new IllegalArgumentException();
            if(!Set.of("1.2.840.113549.1.1.11","1.2.840.113549.1.1.12","1.2.840.113549.1.1.13","1.2.840.10045.4.3.2").contains(csr.getSignatureAlgorithm().getAlgorithm().getId())) throw new IllegalArgumentException();
            keyHash=sha256(key.getEncoded());
        } catch(Exception ignored) { throw new CertificateException(CERTIFICATE_REQUEST_INVALID); }
        UUID id=UUID.randomUUID();
        safe(()->tx.execute(status->{
            lockDevice(input.deviceId());
            jdbc.sql("INSERT INTO security_device_enrollment(id,device_id,csr_pem,public_key_sha256,actor_username) VALUES(:id,:device,:csr,:key,:actor)")
                    .param("id",id).param("device",input.deviceId()).param("csr",pem).param("key",keyHash).param("actor",actor).update();
            change(input.deviceId(),actor,"ENROLLMENT_REQUESTED","CSR público registrado; firma pendiente");return null;
        }));
        return enrollments(input.deviceId()).stream().filter(e->e.id().equals(id)).findFirst().orElseThrow();
    }
    public CertificateRow importCertificate(ImportCertificate input,String actor) {
        requireEnabled();requireDeviceId(input.deviceId());String reason=reason(input.reason());
        X509Certificate certificate=parse(input.certificatePem());
        UUID device=validateCertificate(certificate);
        if(!device.equals(input.deviceId())) fail(CERTIFICATE_INVALID);
        String keyHash=sha256(certificate.getPublicKey().getEncoded());UUID id=UUID.randomUUID();
        var existing=certificates(device).stream().filter(row->row.fingerprintSha256().equals(TrustedClientResolver.fingerprint(certificate))).findFirst();
        if(existing.isPresent()) return existing.get();
        safe(()->tx.execute(status->{
            lockDevice(device);
            var enrollment=jdbc.sql("SELECT id FROM security_device_enrollment WHERE device_id=:device AND public_key_sha256=:key AND status='PENDING' ORDER BY created_at DESC LIMIT 1 FOR UPDATE")
                    .param("device",device).param("key",keyHash).query(UUID.class).optional().orElseThrow(()->new CertificateException(CERTIFICATE_ENROLLMENT_REQUIRED));
            jdbc.sql("""
                    INSERT INTO security_device_certificate(id,device_id,kind,fingerprint_sha256,issuer_id,serial_hex,not_before,expires_at,public_certificate_pem)
                    VALUES(:id,:device,'DEVICE',:fp,:issuer,:serial,:nb,:exp,:pem)
                    """).param("id",id).param("device",device).param("fp",TrustedClientResolver.fingerprint(certificate))
                    .param("issuer",issuerId(certificate)).param("serial",certificate.getSerialNumber().toString(16))
                    .param("nb",java.sql.Timestamp.from(certificate.getNotBefore().toInstant())).param("exp",java.sql.Timestamp.from(certificate.getNotAfter().toInstant()))
                    .param("pem",input.certificatePem()).update();
            jdbc.sql("UPDATE security_device_enrollment SET status='IMPORTED' WHERE id=:id").param("id",enrollment).update();
            change(device,actor,"CERTIFICATE_IMPORTED",reason);return null;
        }));
        return certificates(device).stream().filter(c->c.id().equals(id)).findFirst().orElseThrow();
    }
    public CertificateRow issue(UUID enrollmentId,String rawReason,String actor) {
        requireEnabled();String reason=reason(rawReason);
        if(signer==null || !signer.isConfigured()) fail(CERTIFICATE_SIGNER_UNAVAILABLE);
        var enrollment=safe(()->jdbc.sql("SELECT * FROM security_device_enrollment WHERE id=:id").param("id",enrollmentId)
                .query((rs,n)->new Enrollment(enrollmentId,rs.getObject("device_id",UUID.class),rs.getTimestamp("created_at").toInstant(),rs.getString("status"),rs.getString("public_key_sha256"),rs.getString("csr_pem")))
                .optional().orElseThrow(()->new CertificateException(CERTIFICATE_NOT_FOUND)));
        String pem=signer.sign(enrollmentId,enrollment.deviceId(),enrollment.csrPem());
        return importCertificate(new ImportCertificate(enrollment.deviceId(),pem,reason),actor);
    }
    public String download(UUID certificateId) {
        requireEnabled();return safe(()->jdbc.sql("SELECT public_certificate_pem FROM security_device_certificate WHERE id=:id AND public_certificate_pem IS NOT NULL")
                .param("id",certificateId).query(String.class).optional().orElseThrow(()->new CertificateException(CERTIFICATE_NOT_FOUND)));
    }
    public void hardware(UUID device,HardwareApproval approval,String actor) {
        requireEnabled(); requireDeviceId(device);String reason=reason(approval.reason());
        String evidence=approval.evidenceReference();
        if(approval.approved() && (evidence==null || evidence.strip().length()<10 || evidence.length()>300 || evidence.matches("(?s).*[\\p{Cntrl}].*"))) fail(CERTIFICATE_REQUEST_INVALID);
        safe(()->tx.execute(status->{
            lockDevice(device);
            if(approval.approved() && !jdbc.sql("SELECT EXISTS(SELECT 1 FROM security_device_certificate WHERE device_id=:id AND revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP)")
                    .param("id",device).query(Boolean.class).single()) fail(CERTIFICATE_ENROLLMENT_REQUIRED);
            jdbc.sql("UPDATE security_device SET hardware_approved=:approved,hardware_evidence_reference=:evidence,status=:state WHERE id=:id")
                    .param("approved",approval.approved()).param("evidence",approval.approved()?evidence.strip():null)
                    .param("state",approval.approved()?"ACTIVE":"DISABLED").param("id",device).update();
            if(!approval.approved()) jdbc.sql("UPDATE security_user_access_control SET session_epoch=gen_random_uuid() WHERE user_id IN (SELECT user_id FROM security_user_device_binding WHERE device_id=:id AND revoked_at IS NULL)")
                    .param("id",device).update();
            change(device,actor,approval.approved()?"HARDWARE_HOMOLOGATION_APPROVED":"HARDWARE_APPROVAL_WITHDRAWN",reason);return null;
        }));
    }
    public void revoke(UUID certificateId,String actor,String rawReason) {
        requireEnabled();String reason=reason(rawReason);
        safe(()->tx.execute(status->{
            var device=jdbc.sql("SELECT device_id FROM security_device_certificate WHERE id=:id FOR UPDATE").param("id",certificateId).query(UUID.class)
                    .optional().orElseThrow(()->new CertificateException(CERTIFICATE_NOT_FOUND));
            int changed=jdbc.sql("UPDATE security_device_certificate SET revoked_at=CURRENT_TIMESTAMP WHERE id=:id AND revoked_at IS NULL")
                    .param("id",certificateId).update();
            if(changed>0) {
                jdbc.sql("""
                        INSERT INTO security_ca_revocation_outbox(certificate_id,device_id,certificate_pem,status,last_error_code)
                        SELECT id,device_id,public_certificate_pem,
                          CASE WHEN public_certificate_pem IS NULL THEN 'NEEDS_REVIEW' ELSE 'PENDING' END,
                          CASE WHEN public_certificate_pem IS NULL THEN 'CERTIFICATE_PEM_UNAVAILABLE' ELSE NULL END
                        FROM security_device_certificate WHERE id=:id ON CONFLICT(certificate_id) DO NOTHING
                        """).param("id",certificateId).update();
                change(device,actor,"CERTIFICATE_REVOKED",reason);
            }return null;
        }));
    }
    public List<RolePolicy> policies() {
        requireEnabled();return safe(()->jdbc.sql("SELECT r.id,r.name::text,p.mode FROM roles r LEFT JOIN security_role_device_policy p ON p.role_id=r.id ORDER BY r.id")
                .query((rs,n)->new RolePolicy(rs.getLong(1),rs.getString(2),rs.getString(3))).list());
    }
    public void policy(long id,PolicyChange input,String actor) {
        requireEnabled();String reason=reason(input.reason());
        if(input.mode()==null || !Set.of("PASSWORD_ONLY","LEGACY_BROWSER_HASH","CERTIFICATE_REQUIRED").contains(input.mode())) fail(CERTIFICATE_REQUEST_INVALID);
        if("CERTIFICATE_REQUIRED".equals(input.mode()) && !isReady()) fail(CERTIFICATE_PROXY_NOT_READY);
        safe(()->tx.execute(status->{
            String role=jdbc.sql("SELECT name::text FROM roles WHERE id=:id FOR UPDATE").param("id",id).query(String.class).optional()
                    .orElseThrow(()->new CertificateException(CERTIFICATE_NOT_FOUND));
            if("LEGACY_BROWSER_HASH".equals(input.mode()) && !"CLIENTE".equals(role)) fail(CERTIFICATE_REQUEST_INVALID);
            jdbc.sql("INSERT INTO security_role_device_policy(role_id,mode) VALUES(:id,:mode) ON CONFLICT(role_id) DO UPDATE SET mode=EXCLUDED.mode,updated_at=CURRENT_TIMESTAMP")
                    .param("id",id).param("mode",input.mode()).update();
            jdbc.sql("UPDATE security_user_access_control SET session_epoch=gen_random_uuid() WHERE user_id IN (SELECT id FROM users WHERE role_id=:role)")
                    .param("role",id).update();
            change(null,actor,"ROLE_POLICY_CHANGED",reason);return null;
        }));
    }
    public GeoLocationService.Location eventLocation(UUID eventId) {
        var rows=safe(()->jdbc.sql("""
                SELECT l.* FROM security_access_event e JOIN security_access_location l ON l.correlation_id=e.correlation_id WHERE e.id=:id
                """).param("id",eventId).query((rs,n)->new GeoLocationService.Location(rs.getString("status"),rs.getString("country"),rs.getString("region"),
                rs.getString("city"),(Integer)rs.getObject("accuracy_radius_km"),rs.getString("source"),rs.getString("database_version"),rs.getTimestamp("observed_at").toInstant())).list());
        return rows.isEmpty()?new GeoLocationService.Location("UNKNOWN",null,null,null,null,null,null,null):rows.getFirst();
    }
    public UUID validateCertificate(X509Certificate certificate) {
        try {
            certificate.checkValidity();strongKey(certificate.getPublicKey());
            if(certificate.getBasicConstraints()>=0 || certificate.getExtendedKeyUsage()==null || !certificate.getExtendedKeyUsage().contains("1.3.6.1.5.5.7.3.2")
                    || certificate.getKeyUsage()==null || !certificate.getKeyUsage()[0]) throw new IllegalArgumentException();
            if(!Set.of("1.2.840.113549.1.1.11","1.2.840.113549.1.1.12","1.2.840.113549.1.1.13","1.2.840.10045.4.3.2").contains(certificate.getSigAlgOID())) throw new IllegalArgumentException();
            issuerId(certificate); // Exact configured issuing public CA, not arbitrary CN.
            List<UUID> devices=new ArrayList<>();
            if(certificate.getSubjectAlternativeNames()!=null) for(var san:certificate.getSubjectAlternativeNames()) {
                if(Integer.valueOf(6).equals(san.get(0)) && san.get(1) instanceof String uri && uri.startsWith("urn:imbasac:device:")) devices.add(UUID.fromString(uri.substring(19)));
            }
            if(devices.size()!=1) throw new IllegalArgumentException();return devices.getFirst();
        } catch(java.security.cert.CertificateExpiredException ignored) { throw diagnostic("CERTIFICATE_EXPIRED",CERTIFICATE_INVALID); }
        catch(java.security.cert.CertificateNotYetValidException ignored) { throw diagnostic("CERTIFICATE_NOT_YET_VALID",CERTIFICATE_INVALID); }
        catch(Exception ignored) { throw diagnostic("CERTIFICATE_INVALID",CERTIFICATE_INVALID); }
    }
    private String issuerId(X509Certificate certificate) {
        for(X509Certificate issuer:issuers) try {
            issuer.checkValidity();
            if(issuer.getBasicConstraints()<0 || !certificate.getIssuerX500Principal().equals(issuer.getSubjectX500Principal())) continue;
            certificate.verify(issuer.getPublicKey());return TrustedClientResolver.fingerprint(issuer);
        } catch(Exception ignored) {}
        throw new CertificateException(CERTIFICATE_INVALID);
    }
    private static X509Certificate parse(String pem) {
        try {
            if(pem==null || pem.length()>16000 || !pem.matches("(?s)-----BEGIN CERTIFICATE-----\\r?\\n[A-Za-z0-9+/=\\r\\n]+-----END CERTIFICATE-----[\\r\\n]*")) throw new IllegalArgumentException();
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
        } catch(Exception ignored) { throw new CertificateException(CERTIFICATE_INVALID); }
    }
    private void lockDevice(UUID device) { jdbc.sql("SELECT id FROM security_device WHERE id=:id FOR UPDATE").param("id",device).query(UUID.class).optional().orElseThrow(()->new CertificateException(CERTIFICATE_NOT_FOUND)); }
    private void change(UUID device,String actor,String action,String reason) { jdbc.sql("INSERT INTO security_certificate_change(id,device_id,actor_username,action,reason) VALUES(:id,:device,:actor,:action,:reason)")
            .param("id",UUID.randomUUID()).param("device",device).param("actor",actor).param("action",action).param("reason",reason).update(); }
    private static void strongKey(PublicKey key) {
        if(key instanceof RSAPublicKey rsa && rsa.getModulus().bitLength()>=3072 && rsa.getModulus().bitLength()<=8192) return;
        if(key instanceof ECPublicKey ec) try {
            var parameters=AlgorithmParameters.getInstance("EC");parameters.init(new ECGenParameterSpec("secp256r1"));
            var approved=parameters.getParameterSpec(ECParameterSpec.class);var actual=ec.getParams();
            if(actual!=null && approved.getCurve().equals(actual.getCurve()) && approved.getGenerator().equals(actual.getGenerator())
                    && approved.getOrder().equals(actual.getOrder()) && approved.getCofactor()==actual.getCofactor()) return;
        } catch(Exception ignored) {}
        throw new IllegalArgumentException();
    }
    private static String sha256(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch(Exception e) { throw new IllegalStateException(); } }
    private void requireEnabled() { if(!enabled) fail(CERTIFICATES_DISABLED); }
    private void requireEnrollmentEnabled() { if(!enrollmentEnabled) fail(CERTIFICATES_DISABLED); }
    private static void requireDeviceId(UUID id) { if(id==null) fail(CERTIFICATE_REQUEST_INVALID); }
    private static String reason(String raw) { if(raw==null) throw new CertificateException(CERTIFICATE_REQUEST_INVALID); String value=raw.replaceAll("[\\p{Cntrl}]"," ").strip();if(value.length()<10 || value.length()>300) fail(CERTIFICATE_REQUEST_INVALID);return value; }
    private static void fail(CertificateException.Code code) { throw new CertificateException(code); }
    private <T> T safe(java.util.function.Supplier<T> action) { try { return action.get(); } catch(DataAccessException|org.springframework.transaction.TransactionException ignored) { throw new CertificateException(CERTIFICATE_UNAVAILABLE); } }
}
