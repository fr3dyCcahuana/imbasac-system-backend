package com.paulfernandosr.possystembackend.trustedclient;

import com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException;
import com.paulfernandosr.possystembackend.security.domain.AccessSessionId;
import com.paulfernandosr.possystembackend.user.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.UUID;
import static com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException.Code.*;

/** Durable epoch + exact binding prevent old credentials reviving after unblock/rebind. */
@Service
public class ControlSessionGuard {
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final TrustedClientResolver transport;private final boolean enabled;
    public ControlSessionGuard(JdbcClient jdbc,PlatformTransactionManager manager,TrustedClientResolver transport,
            @Value("${security.access-control.enabled:false}") boolean enabled) {
        this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.transport=transport;this.enabled=enabled;
    }
    public record CredentialSnapshot(Long userId,Long roleId,String passwordHash,boolean enabled) {
        @Override public String toString() { return "CredentialSnapshot[redacted]"; }
    }
    public CredentialSnapshot capture(String username) {
        if(!enabled) return null;
        try { return jdbc.sql("SELECT id,role_id,password,enabled FROM users WHERE username=:username").param("username",username)
                .query((rs,n)->new CredentialSnapshot(rs.getObject("id",Long.class),rs.getObject("role_id",Long.class),rs.getString("password"),rs.getBoolean("enabled")))
                .optional().orElse(null);
        } catch(DataAccessException ignored) { throw new AccessControlException(ACCESS_CONTROL_UNAVAILABLE); }
    }
    public void register(User user,UUID refreshCredential) { register(user,refreshCredential,enabled?capture(user.getUsername()):null); }
    public void register(User user,UUID refreshCredential,CredentialSnapshot snapshot) {
        if(!enabled) return;
        if(snapshot==null || !snapshot.enabled() || !snapshot.userId().equals(user.getId())) throw new AccessControlException(ACCESS_DEVICE_NOT_AUTHORIZED);
        UUID device=authorizedDevice();String fingerprint=device==null?null:TrustedClientResolver.fingerprint(transport.current().certificate());
        try { tx.executeWithoutResult(status->{
            jdbc.sql("""
                    SELECT id FROM users WHERE id=:u AND username=:username AND enabled=true AND role_id=:role
                    AND password IS NOT DISTINCT FROM CAST(:password AS VARCHAR) FOR UPDATE
                    """).param("u",user.getId()).param("username",user.getUsername()).param("role",snapshot.roleId()).param("password",snapshot.passwordHash())
                    .query(Long.class).optional().orElseThrow(()->new AccessControlException(ACCESS_DEVICE_NOT_AUTHORIZED));
            jdbc.sql("INSERT INTO security_user_access_control(user_id,cycle_id) VALUES(:u,:cycle) ON CONFLICT(user_id) DO NOTHING")
                    .param("u",user.getId()).param("cycle",UUID.randomUUID()).update();
            int inserted=jdbc.sql("""
                    INSERT INTO security_control_session(access_session_id,user_id,security_epoch,device_id,binding_id,certificate_fingerprint)
                    SELECT :sid,:u,c.session_epoch,:device,
                    (SELECT id FROM security_user_device_binding WHERE user_id=:u AND device_id=:device AND revoked_at IS NULL),:fp
                    FROM security_user_access_control c WHERE c.user_id=:u AND (c.blocked_until IS NULL OR c.blocked_until<=CURRENT_TIMESTAMP)
                    AND (CAST(:device AS UUID) IS NULL OR EXISTS(SELECT 1 FROM security_user_device_binding b JOIN security_device d ON d.id=b.device_id
                        WHERE b.user_id=:u AND b.device_id=:device AND b.revoked_at IS NULL AND d.status='ACTIVE' AND d.hardware_approved=true))
                    """).param("sid",AccessSessionId.fromRefreshCredential(refreshCredential)).param("u",user.getId()).param("device",device).param("fp",fingerprint).update();
            if(inserted!=1) throw new AccessControlException(ACCESS_ACCOUNT_BLOCKED);
        }); } catch(DataAccessException|org.springframework.transaction.TransactionException ignored) { throw new AccessControlException(ACCESS_CONTROL_UNAVAILABLE); }
    }
    public boolean active(String sid,String username,boolean verifyDevice) {
        if(!enabled) return true;
        try {
            UUID device=verifyDevice?authorizedDevice():null;
            String fp=device==null?null:TrustedClientResolver.fingerprint(transport.current().certificate());
            return jdbc.sql("""
                    SELECT EXISTS(SELECT 1 FROM security_control_session s
                    JOIN security_user_access_control c ON c.user_id=s.user_id AND c.session_epoch=s.security_epoch
                    JOIN users u ON u.id=s.user_id
                    LEFT JOIN security_user_device_binding b ON b.id=s.binding_id
                    WHERE s.access_session_id=:sid AND u.username=:username AND u.enabled=true
                    AND (c.blocked_until IS NULL OR c.blocked_until<=CURRENT_TIMESTAMP)
                    AND (s.device_id IS NULL OR (b.id IS NOT NULL AND b.revoked_at IS NULL
                      AND EXISTS(SELECT 1 FROM security_device d WHERE d.id=s.device_id AND d.status='ACTIVE' AND d.hardware_approved=true)
                      AND EXISTS(SELECT 1 FROM security_device_certificate cert WHERE cert.device_id=s.device_id
                        AND cert.fingerprint_sha256=s.certificate_fingerprint AND cert.revoked_at IS NULL
                        AND cert.not_before<=CURRENT_TIMESTAMP AND cert.expires_at>CURRENT_TIMESTAMP)))
                    AND (:verify=false OR (s.device_id IS NULL AND CAST(:device AS UUID) IS NULL) OR
                      (s.device_id=:device AND s.certificate_fingerprint=:fp AND b.id IS NOT NULL AND b.revoked_at IS NULL
                       AND EXISTS(SELECT 1 FROM security_device_certificate cert WHERE cert.device_id=s.device_id
                           AND cert.fingerprint_sha256=s.certificate_fingerprint AND cert.revoked_at IS NULL
                           AND cert.expires_at>CURRENT_TIMESTAMP))))
                    """).param("sid",sid).param("username",username).param("verify",verifyDevice).param("device",device).param("fp",fp).query(Boolean.class).single();
        } catch(DataAccessException ignored) { throw new AccessControlException(ACCESS_CONTROL_UNAVAILABLE); }
    }
    public void checkRefresh(UUID credential,String username) {
        if(!active(AccessSessionId.fromRefreshCredential(credential),username,true)) throw new AccessControlException(ACCESS_DEVICE_NOT_AUTHORIZED);
    }
    private static UUID authorizedDevice() {
        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                && attrs.getRequest().getAttribute("imbasac.authorizedDeviceId") instanceof UUID device) return device;
        return null;
    }
}
