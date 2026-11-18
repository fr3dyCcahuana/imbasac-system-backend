package com.paulfernandosr.possystembackend.deviceaccess.application;
import com.paulfernandosr.possystembackend.deviceaccess.domain.*;
import com.paulfernandosr.possystembackend.deviceaccess.domain.port.output.DevicePolicyRepository;
import com.paulfernandosr.possystembackend.role.domain.RoleName;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.paulfernandosr.possystembackend.accesscontrol.application.AccessControlService;
import com.paulfernandosr.possystembackend.devicecertificate.DeviceCertificateService;
import com.paulfernandosr.possystembackend.devicecertificate.CertificateException;
import com.paulfernandosr.possystembackend.trustedclient.TrustedClientResolver;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import static com.paulfernandosr.possystembackend.deviceaccess.domain.DevicePolicyException.Code.*;

@Service
public class DeviceAccessPolicyService {
    private final DevicePolicyRepository policies;
    private final UserRepository users;
    private final boolean enabled;
    @Value("${security.device-policy.individual-enabled:false}")
    private boolean individualEnabled;
    private final DeviceCertificateService certificates;
    private final AccessControlService control;
    private final TrustedClientResolver transport;
    public DeviceAccessPolicyService(DevicePolicyRepository policies, UserRepository users,
            boolean enabled) { this(policies,users,enabled,null,null,null); }
    @Autowired public DeviceAccessPolicyService(DevicePolicyRepository policies, UserRepository users,
            @Value("${security.device-policy.enabled:false}") boolean enabled,
            DeviceCertificateService certificates,AccessControlService control,TrustedClientResolver transport) {
        this.policies=policies; this.users=users; this.enabled=enabled;this.certificates=certificates;this.control=control;this.transport=transport;
    }
    public boolean isEnabled() { return enabled || control!=null && control.isEnabled(); }
    public void check(User user) {
        if(control!=null) control.checkAccount(user);
        // Compatibility path: no new table/query or change to existing authentication.
        if (!enabled) return;
        DeviceAccessMode mode=resolve(user);
        if (mode == DeviceAccessMode.CERTIFICATE_REQUIRED) {
            if(certificates==null || !certificates.isReady()) throw new DevicePolicyException(DEVICE_CERTIFICATE_CHANNEL_NOT_READY);
            if(control==null || !control.isEnabled()) throw new DevicePolicyException(DEVICE_POLICY_UNAVAILABLE);
            try {
                var device=certificates.presentedDevice();
                if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)
                    attrs.getRequest().setAttribute("imbasac.observedDeviceId",device);
                if(!control.isDeviceAuthorized(user,device)) {
                    com.paulfernandosr.possystembackend.accessaudit.domain.AuditFailure.note("CERTIFICATE_PC_NOT_AUTHORIZED_FOR_USER");
                    throw new DevicePolicyException(DEVICE_NOT_AUTHORIZED);
                }
                if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)
                    attrs.getRequest().setAttribute("imbasac.authorizedDeviceId",device);
            } catch(CertificateException error) {
                throw new DevicePolicyException(switch(error.getCode()) {
                    case CERTIFICATE_UNAVAILABLE -> DEVICE_POLICY_UNAVAILABLE;
                    case CERTIFICATE_INVALID -> DEVICE_CERTIFICATE_INVALID;
                    default -> DEVICE_NOT_AUTHORIZED;
                });
            }
        }
    }
    public void checkUsername(String username) {
        if (!isEnabled()) return;
        check(findUser(username));
    }
    public DevicePolicyStatus status(String username) {
        User user=findUser(username);
        DeviceAccessMode mode=resolve(user);
        return new DevicePolicyStatus(mode, enabled, certificates!=null && certificates.isReady());
    }
    /** Called exclusively after credential verification; normal requests and refresh never create strikes. */
    public void recordUnauthorizedLogin(User user,DevicePolicyException error) {
        if(error.getCode()!=DEVICE_NOT_AUTHORIZED || control==null || !control.isEnabled() || transport==null) return;
        var context=transport.current();
        if(!context.proxyAuthenticated() || !("SUCCESS".equals(context.certificateState()) || "NONE".equals(context.certificateState()))) return;
        if(certificates.isRegisteredForUser(user.getId(),context.certificate())) return;
        try {
            String origin=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    ("imbasac-origin-v1|"+context.clientIp()).getBytes(StandardCharsets.UTF_8)));
            java.util.UUID observed=null;
            if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                    && attrs.getRequest().getAttribute("imbasac.observedDeviceId") instanceof java.util.UUID device) observed=device;
            control.rejectUnauthorizedDevice(user,observed,context.requestId(),origin);
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(); }
    }
    private User findUser(String username) {
        try {
            return users.findByUsername(username).orElseThrow(() -> new DevicePolicyException(DEVICE_USER_NOT_AUTHORIZED));
        } catch (DataAccessException exception) {
            throw new DevicePolicyException(DEVICE_POLICY_UNAVAILABLE);
        }
    }
    private DeviceAccessMode resolve(User user) {
        if (user == null || !user.isEnabled() || user.getRole() == null || user.getRole().getName() == null) {
            throw new DevicePolicyException(DEVICE_USER_NOT_AUTHORIZED);
        }
        RoleName role=user.getRole().getName();
        if (!enabled) {
            // Explicit compatibility mapping. Unknown/missing roles never mean an exemption.
            return switch(role) {
                case CLIENTE -> DeviceAccessMode.LEGACY_BROWSER_HASH;
                case ADMINISTRATOR, CASHIER, WHOLESALER, WAREHOUSE -> DeviceAccessMode.PASSWORD_ONLY;
            };
        }
        if (user.getRole().getId() == null) throw new DevicePolicyException(DEVICE_POLICY_NOT_CONFIGURED);
        try {
            var configuredMode=individualEnabled
                    ? policies.findForUser(user.getId(),user.getRole().getId())
                    : policies.findByRole(user.getRole().getId(),role);
            DeviceAccessMode mode=configuredMode
                    .orElseThrow(() -> new DevicePolicyException(DEVICE_POLICY_NOT_CONFIGURED));
            if (mode == DeviceAccessMode.LEGACY_BROWSER_HASH && role != RoleName.CLIENTE) {
                throw new DevicePolicyException(DEVICE_POLICY_NOT_CONFIGURED);
            }
            return mode;
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw new DevicePolicyException(DEVICE_POLICY_UNAVAILABLE);
        }
    }
}
